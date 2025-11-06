package com.spectrayan.sse.server.controller;

import com.spectrayan.sse.server.config.SseHeaderHandler;
import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.customize.SseEndpointCustomizer;
import com.spectrayan.sse.server.customize.SseHeaderCustomizer;
import com.spectrayan.sse.server.customize.SseStreamCustomizer;
import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.session.SseSession;
import com.spectrayan.sse.server.error.ErrorEvents;
import com.spectrayan.sse.server.error.SseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Supplier;

/**
 * Functional endpoint handler for SSE streaming. Mirrors the behavior in {@link SseController}
 * but is intended for use with a functional {@code RouterFunction}.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SseEndpointHandler {

    private static final Logger log = LoggerFactory.getLogger(SseEndpointHandler.class);

    private final SseEmitter sseEmitter;
    private final SseHeaderHandler headerHandler;
    private final SseServerProperties props;
    private final List<SseStreamCustomizer> streamCustomizers;
    private final List<SseHeaderCustomizer> headerCustomizers;
    private final List<SseEndpointCustomizer> endpointCustomizers;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public SseEndpointHandler(SseEmitter sseEmitter,
                              SseHeaderHandler headerHandler,
                              SseServerProperties props,
                              ObjectProvider<SseStreamCustomizer> streamCustomizers,
                              ObjectProvider<SseHeaderCustomizer> headerCustomizers,
                              ObjectProvider<SseEndpointCustomizer> endpointCustomizers,
                              org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.sseEmitter = sseEmitter;
        this.headerHandler = headerHandler;
        this.props = props;
        this.streamCustomizers = streamCustomizers.orderedStream().toList();
        this.headerCustomizers = headerCustomizers.orderedStream().toList();
        this.endpointCustomizers = endpointCustomizers.orderedStream().toList();
        this.eventPublisher = eventPublisher;
    }

    public Mono<ServerResponse> handle(ServerRequest request) {
        String topic = request.pathVariable("topic");
        ServerWebExchange exchange = request.exchange();
        String remote = exchange.getRequest().getRemoteAddress() != null ? exchange.getRequest().getRemoteAddress().toString() : "unknown";

        return exchange.getSession()
                .map(ws -> ws != null ? ws.getId() : "")
                .filter(id -> id != null && !id.isBlank())
                .switchIfEmpty(reactor.core.publisher.Mono.fromSupplier(() -> java.util.UUID.randomUUID().toString()))
                .flatMap(sessionId -> {
                    // Publish session created event
                    try {
                        eventPublisher.publishEvent(new com.spectrayan.sse.server.events.SseSessionCreatedEvent(sessionId, topic, remote));
                    } catch (Throwable t) {
                        log.debug("Failed to publish SseSessionCreatedEvent: {}", t.toString());
                    }

                    // Build the core behavior supplier (same as controller)
                    Supplier<Flux<ServerSentEvent<Object>>> core = () -> buildFlux(sessionId, topic, remote, exchange);

                    // Wrap with endpoint customizers (outermost first)
                    Supplier<Flux<ServerSentEvent<Object>>> chain = core;
                    for (int i = endpointCustomizers.size() - 1; i >= 0; i--) {
                        SseEndpointCustomizer customizer = endpointCustomizers.get(i);
                        Supplier<Flux<ServerSentEvent<Object>>> next = chain;
                        chain = () -> customizer.handle(topic, exchange, next);
                    }

                    Flux<ServerSentEvent<Object>> flux = chain.get();
                    return ServerResponse.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(flux, ServerSentEvent.class);
                });
    }

    private Flux<ServerSentEvent<Object>> buildFlux(String sessionId, String topic, String remote, ServerWebExchange exchange) {
        var request = exchange.getRequest();

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

        log.info("SSE subscription requested (router): topic={} from {}", topic, remote);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        SseSession session = SseSession.builder()
                .sessionId(sessionId)
                .topic(topic)
                .remoteAddress(remote)
                .userAgent(userAgent)
                .build();
        Flux<ServerSentEvent<Object>> flux = sseEmitter.connect(topic, session)
                .doOnSubscribe(s -> {
                    log.debug("SSE stream subscribed: topic={} from {}", topic, remote);
                    try {
                        eventPublisher.publishEvent(new com.spectrayan.sse.server.events.SseSubscribedEvent(sessionId, topic, remote));
                    } catch (Throwable t) {
                        log.debug("Failed to publish SseSubscribedEvent: {}", t.toString());
                    }
                })
                .doFinally(sig -> {
                    try {
                        switch (sig) {
                            case CANCEL -> eventPublisher.publishEvent(new com.spectrayan.sse.server.events.SseUnsubscribedEvent(sessionId, topic, remote));
                            case ON_COMPLETE -> eventPublisher.publishEvent(new com.spectrayan.sse.server.events.SseSessionClosedEvent(sessionId, topic, remote));
                            default -> {}
                        }
                    } catch (Throwable t) {
                        log.debug("Failed to publish finalization event: {}", t.toString());
                    }
                });

        // Prepend retry line if enabled
        if (props.getStream().isRetryEnabled()) {
            flux = Flux.concat(Flux.just(ServerSentEvent.<Object>builder().retry(props.getStream().getRetry()).build()), flux);
        }

        // Map errors to SSE if configured
        if (props.getStream().isMapErrorsToSse()) {
            flux = flux
                .doOnError(ex -> {
                    log.warn("SSE stream error: topic={} from {} error={}", topic, remote, ex.toString());
                    try {
                        eventPublisher.publishEvent(new com.spectrayan.sse.server.events.SseDisconnectedEvent(sessionId, topic, remote, ex));
                    } catch (Throwable t) {
                        log.debug("Failed to publish SseDisconnectedEvent: {}", t.toString());
                    }
                })
                .onErrorResume(ex -> Mono.deferContextual(ctx -> {
                    if (ex instanceof SseException se) {
                        return Mono.just(ErrorEvents.fromException(se, topic, ctx));
                    }
                    return Mono.just(ErrorEvents.fromThrowable(ex, topic, ctx));
                }));
        }

        // Add topic + session + remote address + MDC activation marker into context
        flux = flux.contextWrite(ctx -> ctx
                .put(props.getMdcContextKey(), Boolean.TRUE)
                .put("topic", topic)
                .put("sessionId", sessionId)
                .put("remoteAddress", remote)
        );

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
