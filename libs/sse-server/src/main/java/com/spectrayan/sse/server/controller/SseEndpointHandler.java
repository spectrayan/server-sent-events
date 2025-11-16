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
 * Functional endpoint handler for Server‑Sent Events (SSE) streaming to be used with
 * Spring WebFlux functional routing (e.g., {@code RouterFunction}).
 * <p>
 * Overview:
 * - Exposes a handler method {@link #handle(org.springframework.web.reactive.function.server.ServerRequest)}
 *   that returns a {@link org.springframework.web.reactive.function.server.ServerResponse} with
 *   {@link org.springframework.http.MediaType#TEXT_EVENT_STREAM} content type.
 * - Orchestrates header preparation, session lifecycle events, stream customization, error mapping,
 *   retry/connected/heartbeat behavior as configured in {@link com.spectrayan.sse.server.config.SseServerProperties}.
 * <p>
 * Key behaviors:
 * - Session and events:
 *   - Resolves a session id from the WebSession when available; otherwise generates one via
 *     {@link com.spectrayan.sse.server.customize.SessionIdGenerator} (fallback to random UUID).
 *   - Publishes lifecycle events via Spring's ApplicationEventPublisher:
 *     {@code SseSessionCreatedEvent}, {@code SseSubscribedEvent},
 *     {@code SseUnsubscribedEvent}/{@code SseSessionClosedEvent} and {@code SseDisconnectedEvent} on error.
 * - Headers:
 *   - Applies configured response headers through {@link com.spectrayan.sse.server.config.SseHeaderHandler}
 *     and allows further mutation via registered {@link com.spectrayan.sse.server.customize.SseHeaderCustomizer}s.
 * - Stream customization chain:
 *   - Builds the core {@link reactor.core.publisher.Flux} from {@link com.spectrayan.sse.server.emitter.SseEmitter#connect(String, com.spectrayan.sse.server.session.SseSession)}
 *     and then applies ordered {@link com.spectrayan.sse.server.customize.SseStreamCustomizer}s.
 *   - Wrapps the core behavior with ordered {@link com.spectrayan.sse.server.customize.SseEndpointCustomizer}s allowing
 *     cross‑cutting concerns (auth, metrics, rate‑limit, etc.).
 * - Error handling:
 *   - When {@code stream.mapErrorsToSse=true}, maps exceptions to SSE using {@link com.spectrayan.sse.server.error.ErrorEvents}.
 * - Retry/Context:
 *   - Prepends a {@code retry: <millis>} line when enabled.
 *   - Adds MDC context entries (topic, sessionId, remoteAddress) under the configured key.
 * <p>
 * Typical router usage:
 * <pre>{@code
 * org.springframework.web.reactive.function.server.RouterFunction<
 *     org.springframework.web.reactive.function.server.ServerResponse> routes(
 *     SseEndpointHandler handler) {
 *   return org.springframework.web.reactive.function.server.RouterFunctions.route()
 *       .GET("/sse/{topic}", handler::handle)
 *       .build();
 * }
 * }</pre>
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
    private final com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator;

    /**
         * Create a new {@code SseEndpointHandler}.
         * <p>
         * Dependency roles:
         * - {@link SseEmitter}: connects subscribers to a topic and returns the core {@link Flux} of SSE items.
         * - {@link SseHeaderHandler}: applies configured static and request-propagated headers to the response.
         * - {@link SseServerProperties}: toggles behavior such as retry line, error mapping, and MDC context key.
         * - {@code streamCustomizers}: ordered chain applied to the built stream for per-endpoint adjustments.
         * - {@code headerCustomizers}: ordered customizers for last-mile header mutations.
         * - {@code endpointCustomizers}: outer wrappers around the core stream supplier (cross-cutting concerns).
         * - {@code eventPublisher}: publishes lifecycle events (created, subscribed, unsubscribed/closed, disconnected).
         * - {@code sessionIdGenerator}: optional generator for session id when WebSession id is not available.
         *
         * @implNote Customizers are obtained as ordered streams so their order can be controlled via Spring ordering.
         */
        public SseEndpointHandler(SseEmitter sseEmitter,
                              SseHeaderHandler headerHandler,
                              SseServerProperties props,
                              ObjectProvider<SseStreamCustomizer> streamCustomizers,
                              ObjectProvider<SseHeaderCustomizer> headerCustomizers,
                              ObjectProvider<SseEndpointCustomizer> endpointCustomizers,
                              org.springframework.context.ApplicationEventPublisher eventPublisher,
                              com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator) {
        this.sseEmitter = sseEmitter;
        this.headerHandler = headerHandler;
        this.props = props;
        this.streamCustomizers = streamCustomizers.orderedStream().toList();
        this.headerCustomizers = headerCustomizers.orderedStream().toList();
        this.endpointCustomizers = endpointCustomizers.orderedStream().toList();
        this.eventPublisher = eventPublisher;
        this.sessionIdGenerator = sessionIdGenerator;
    }

    /**
         * Handle an incoming SSE subscription request from a functional WebFlux route.
         * <p>
         * Responsibilities performed by this method:
         * - Resolve the target {@code topic} from the path variable {@code /sse/{topic}}.
         * - Resolve a session id from the current {@link org.springframework.web.server.WebSession} when present;
         *   otherwise generate one via the configured {@link com.spectrayan.sse.server.customize.SessionIdGenerator}
         *   (falling back to a random UUID if no generator is configured).
         * - Publish {@code SseSessionCreatedEvent} before stream construction.
         * - Build the stream by invoking {@link #buildFlux(String, String, String, ServerWebExchange)} and wrap it with
         *   any registered {@link com.spectrayan.sse.server.customize.SseEndpointCustomizer}s (outermost first).
         * - Return a {@link ServerResponse} with content type {@link MediaType#TEXT_EVENT_STREAM} whose body is the
         *   resulting {@link Flux} of {@link ServerSentEvent}s.
         * <p>
         * Error handling and mapping to SSE (when enabled) as well as retry line emission and MDC context population
         * happen inside {@link #buildFlux(String, String, String, ServerWebExchange)}.
         *
         * @param request the incoming functional {@link ServerRequest}
         * @return a {@link Mono} that emits the {@link ServerResponse} configured for SSE streaming
         */
        public Mono<ServerResponse> handle(ServerRequest request) {
        String topic = request.pathVariable("topic");
        ServerWebExchange exchange = request.exchange();
        String remote = exchange.getRequest().getRemoteAddress() != null ? exchange.getRequest().getRemoteAddress().toString() : "unknown";

        return exchange.getSession()
                .map(ws -> ws != null ? ws.getId() : "")
                .filter(id -> id != null && !id.isBlank())
                .switchIfEmpty(reactor.core.publisher.Mono.fromSupplier(() -> sessionIdGenerator != null ? sessionIdGenerator.generate(exchange, topic) : java.util.UUID.randomUUID().toString()))
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

    /**
         * Build the {@link Flux} of {@link ServerSentEvent} for a resolved session/topic.
         * <p>
         * Processing pipeline:
         * - Applies configured response headers via {@link SseHeaderHandler} and then invokes any
         *   registered {@link SseHeaderCustomizer}s (ordered).
         * - Logs subscription and publishes lifecycle events on subscribe and termination:
         *   {@code SseSubscribedEvent} on subscribe; {@code SseUnsubscribedEvent} on cancel; {@code SseSessionClosedEvent}
         *   on completion.
         * - Delegates to {@link SseEmitter#connect(String, com.spectrayan.sse.server.session.SseSession)} to obtain
         *   the core stream for the topic and session.
         * - Optionally prepends a single {@code retry: <millis>} line when enabled via properties.
         * - When {@code stream.mapErrorsToSse=true}, logs the error, publishes {@code SseDisconnectedEvent}, and
         *   maps the error to a structured SSE via {@link ErrorEvents}.
         * - Enriches Reactor context with MDC activation flag and keys: {@code topic}, {@code sessionId}, {@code remoteAddress}.
         * - Applies ordered {@link SseStreamCustomizer}s to allow downstream transformation of the stream.
         *
         * This method is internal to the handler; external callers should use {@link #handle(ServerRequest)}.
         *
         * @param sessionId resolved or generated session identifier
         * @param topic the topic path variable determining which stream to subscribe to
         * @param remote a textual representation of the remote address (for logging/events)
         * @param exchange the current {@link ServerWebExchange}
         * @return a composed {@link Flux} of {@link ServerSentEvent} items ready to be written as SSE
         */
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
