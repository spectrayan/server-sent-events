package com.spectrayan.sse.server.controller;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.customize.SseHeaderCustomizer;
import com.spectrayan.sse.server.customize.SseStreamCustomizer;
import com.spectrayan.sse.server.config.SseHeaderHandler;
import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.error.ErrorEvents;
import com.spectrayan.sse.server.error.SseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * HTTP API for Server-Sent Events (SSE).
 * <p>
 * Exposes a single endpoint that clients can subscribe to using the standard EventSource API
 * (or any SSE-capable client). Each {@code topic} corresponds to a logical stream managed by
 * {@link SseEmitter}.
 */
@RestController
@RequestMapping("${spectrayan.sse.server.controller.base-path:/sse}")
@ConditionalOnBooleanProperties({
        @ConditionalOnBooleanProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = true, matchIfMissing = true),
        @ConditionalOnBooleanProperty(prefix = "spectrayan.sse.server.controller", name = "router-enabled", havingValue = false, matchIfMissing = true)

})
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final SseEmitter sseEmitter;
    private final SseHeaderHandler headerHandler;
    private final SseServerProperties props;
    private final List<SseStreamCustomizer> streamCustomizers;
    private final List<SseHeaderCustomizer> headerCustomizers;

    /**
     * Construct the controller with the shared {@link SseEmitter} and header handler.
     */
    public SseController(SseEmitter sseEmitter,
                         SseHeaderHandler headerHandler,
                         SseServerProperties props,
                         ObjectProvider<SseStreamCustomizer> streamCustomizers,
                         ObjectProvider<SseHeaderCustomizer> headerCustomizers) {
        this.sseEmitter = sseEmitter;
        this.headerHandler = headerHandler;
        this.props = props;
        this.streamCustomizers = streamCustomizers.orderedStream().toList();
        this.headerCustomizers = headerCustomizers.orderedStream().toList();
    }

    /**
     * Subscribe to a topic as a Server-Sent Events stream.
     */
    @GetMapping(path = "/{topic}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@PathVariable String topic, ServerWebExchange exchange) {
        var request = exchange.getRequest();
        String remote = request.getRemoteAddress() != null ? request.getRemoteAddress().toString() : "unknown";

        // Apply configured response headers (static + copy from request) via handler
        headerHandler.applyResponseHeaders(exchange);
        // Allow custom header mutations
        for (SseHeaderCustomizer c : headerCustomizers) {
            try {
                c.customize(exchange, exchange.getResponse().getHeaders());
            } catch (Throwable t) {
                log.warn("Header customizer {} failed: {}", c.getClass().getSimpleName(), t.toString());
            }
        }

        log.info("SSE subscription requested: topic={} from {}", topic, remote);
        Flux<ServerSentEvent<Object>> flux = sseEmitter.connect(topic)
                .doOnSubscribe(s -> log.debug("SSE stream subscribed: topic={} from {}", topic, remote));

        // Prepend retry line if enabled
        if (props.getStream().isRetryEnabled()) {
            long retryMs = props.getStream().getRetry().toMillis();
            flux = Flux.concat(Flux.just(ServerSentEvent.<Object>builder().retry(props.getStream().getRetry()).build()), flux);
        }

        // Map errors to SSE if configured
        if (props.getStream().isMapErrorsToSse()) {
            flux = flux
                .doOnError(ex -> log.warn("SSE stream error: topic={} from {} error={}", topic, remote, ex.toString()))
                .onErrorResume(ex -> Mono.deferContextual(ctx -> {
                    if (ex instanceof SseException se) {
                        return Mono.just(ErrorEvents.fromException(se, topic, ctx));
                    }
                    return Mono.just(ErrorEvents.fromThrowable(ex, topic, ctx));
                }));
        }

        // Add topic + MDC activation marker into context
        flux = flux.contextWrite(ctx -> ctx.put(props.getMdcContextKey(), Boolean.TRUE).put("topic", topic));

        // Apply stream customizers
        for (SseStreamCustomizer c : streamCustomizers) {
            try {
                flux = c.customize(topic, exchange, flux);
            } catch (Throwable t) {
                log.warn("Stream customizer {} failed: {}", c.getClass().getSimpleName(), t.toString());
            }
        }

        return flux;
    }
}
