package com.spectrayan.sse.server.controller;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.customize.SseStreamCustomizer;
import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.error.ErrorEvents;
import com.spectrayan.sse.server.error.SseException;
import com.spectrayan.sse.server.session.SseSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Shared orchestration logic for SSE stream construction, used by both
 * {@link SseEndpointHandler} and {@link com.spectrayan.sse.server.template.DefaultSseTemplate}.
 * <p>
 * Centralizes:
 * - Subscriber lifecycle event publishing (subscribed, unsubscribed, closed, disconnected)
 * - Retry line prepending
 * - Error-to-SSE mapping
 * - Reactor context enrichment (MDC, topic, session)
 * - Stream customizer application
 * <p>
 * This class is public to allow cross-package access but is an internal
 * implementation detail — not part of the public library API.
 */
public final class SseStreamOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SseStreamOrchestrator.class);

    private final SseEmitter emitter;
    private final SseServerProperties props;
    private final ApplicationEventPublisher eventPublisher;
    private final List<SseStreamCustomizer> streamCustomizers;

    public SseStreamOrchestrator(SseEmitter emitter,
                          SseServerProperties props,
                          ApplicationEventPublisher eventPublisher,
                          List<SseStreamCustomizer> streamCustomizers) {
        this.emitter = emitter;
        this.props = props;
        this.eventPublisher = eventPublisher;
        this.streamCustomizers = streamCustomizers != null ? streamCustomizers : List.of();
    }

    /**
     * Build a fully-decorated SSE stream for the given session.
     * <p>
     * Pipeline:
     * 1. Connect to the emitter to get the core topic flux
     * 2. Attach lifecycle event publishers (subscribe/finalize)
     * 3. Prepend retry line if configured
     * 4. Map errors to SSE error events if configured
     * 5. Enrich Reactor context with MDC keys
     * 6. Apply stream customizers
     *
     * @param session  the resolved SSE session
     * @param exchange the current exchange, may be {@code null} (template path)
     * @return the fully composed SSE flux
     */
    public Flux<ServerSentEvent<Object>> buildStream(SseSession session, ServerWebExchange exchange) {
        String topic = session.getTopic();
        String sessionId = session.getSessionId();
        String remote = session.getRemoteAddress() != null ? session.getRemoteAddress() : "";

        log.info("SSE subscription requested: topic={} from {}", topic, remote);

        Flux<ServerSentEvent<Object>> flux = emitter.connect(topic, session)
                .doOnSubscribe(s -> {
                    log.debug("SSE stream subscribed: topic={} from {}", topic, remote);
                    safePublish(new com.spectrayan.sse.server.events.SseSubscribedEvent(sessionId, topic, remote));
                })
                .doFinally(sig -> {
                    try {
                        switch (sig) {
                            case CANCEL -> safePublish(new com.spectrayan.sse.server.events.SseUnsubscribedEvent(sessionId, topic, remote));
                            case ON_COMPLETE -> safePublish(new com.spectrayan.sse.server.events.SseSessionClosedEvent(sessionId, topic, remote));
                            default -> {}
                        }
                    } catch (Throwable t) {
                        log.debug("Failed to publish finalization event: {}", t.toString());
                    }
                });

        // Prepend retry line if enabled
        if (props.getStream().isRetryEnabled()) {
            flux = Flux.concat(
                    Flux.just(ServerSentEvent.<Object>builder().retry(props.getStream().getRetry()).build()),
                    flux);
        }

        // Map errors to SSE if configured
        if (props.getStream().isMapErrorsToSse()) {
            flux = flux
                .doOnError(ex -> {
                    log.warn("SSE stream error: topic={} from {} error={}", topic, remote, ex.toString());
                    safePublish(new com.spectrayan.sse.server.events.SseDisconnectedEvent(sessionId, topic, remote, ex));
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

    private void safePublish(Object event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Throwable t) {
            log.debug("Failed to publish event {}: {}", event.getClass().getSimpleName(), t.toString());
        }
    }
}
