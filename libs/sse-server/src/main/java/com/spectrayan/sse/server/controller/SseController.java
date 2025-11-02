package com.spectrayan.sse.server.controller;

import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.error.ErrorEvents;
import com.spectrayan.sse.server.error.SseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * HTTP API for Server-Sent Events (SSE).
 * <p>
 * Exposes a single endpoint that clients can subscribe to using the standard EventSource API
 * (or any SSE-capable client). Each {@code topic} corresponds to a logical stream managed by
 * {@link SseEmitter}.
 */
@RestController
@RequestMapping
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final SseEmitter sseEmitter;

    /**
     * Construct the controller with the shared {@link SseEmitter}.
     *
     * @param sseEmitter the SSE emitter component handling topic streams
     */
    public SseController(SseEmitter sseEmitter) {
        this.sseEmitter = sseEmitter;
    }

    /**
     * Subscribe to a topic as a Server-Sent Events stream.
     * <p>
     * Produces {@code text/event-stream}. A small "connected" message is emitted on subscribe to
     * help clients verify connectivity.
     *
     * <p>Example (browser):
     * <pre>
     *   const es = new EventSource('/orders');
     *   es.onmessage = e => console.log('data', e.data);
     *   es.addEventListener('orderCreated', e => console.log('order', e.data));
     * </pre>
     *
     * @param topic the topic path segment to subscribe to (e.g. {@code orders})
     * @return a {@link Flux} of {@link ServerSentEvent} objects emitted for the topic
     */
    @GetMapping(path = "/{topic}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@PathVariable String topic, ServerHttpRequest request) {
        String remote = request.getRemoteAddress() != null ? request.getRemoteAddress().toString() : "unknown";
        log.info("SSE subscription requested: topic={} from {}", topic, remote);
        return sseEmitter.connect(topic)
                .doOnSubscribe(s -> {
                    log.debug("SSE stream subscribed: topic={} from {}", topic, remote);
                    sseEmitter.emitToTopic(topic, "connected");
                })
                .doOnCancel(() -> log.info("SSE subscription cancelled: topic={} from {}", topic, remote))
                .doOnComplete(() -> log.info("SSE stream completed: topic={} from {}", topic, remote))
                .doOnError(ex -> log.warn("SSE stream error: topic={} from {} error={}", topic, remote, ex.toString()))
                .onErrorResume(ex -> Mono.deferContextual(ctx -> {
                    if (ex instanceof SseException se) {
                        return Mono.just(ErrorEvents.fromException(se, topic, ctx));
                    }
                    return Mono.just(ErrorEvents.fromThrowable(ex, topic, ctx));
                }))
                .contextWrite(ctx -> ctx.put("topic", topic));
    }
}
