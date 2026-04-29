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
    private final SseStreamOrchestrator orchestrator;

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
         * <p>
         * Note: Customizers are obtained as ordered streams so their order can be controlled via Spring ordering.
         *
         * @param sseEmitter emitter used to connect clients and emit SSEs
         * @param headerHandler helper that applies SSE headers
         * @param props configuration properties
         * @param streamCustomizers provider of stream customizers (ordered)
         * @param headerCustomizers provider of header customizers (ordered)
         * @param endpointCustomizers provider of endpoint customizers (ordered)
         * @param eventPublisher Spring application event publisher
         * @param sessionIdGenerator generator for session identifiers
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
        this.orchestrator = new SseStreamOrchestrator(sseEmitter, props, eventPublisher, this.streamCustomizers);
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
         * - Build the stream by invoking {@code buildFlux} and wrap it with
         *   any registered {@link com.spectrayan.sse.server.customize.SseEndpointCustomizer}s (outermost first).
         * - Return a {@link ServerResponse} with content type {@link MediaType#TEXT_EVENT_STREAM} whose body is the
         *   resulting {@link Flux} of {@link ServerSentEvent}s.
         * <p>
         * Error handling and mapping to SSE (when enabled) as well as retry line emission and MDC context population
         * happen inside the {@link SseStreamOrchestrator}.
         *
         * @param request the incoming functional {@link ServerRequest}
         * @return a {@link Mono} that emits the {@link ServerResponse} configured for SSE streaming
         */
        public Mono<ServerResponse> handle(ServerRequest request) {
        String topic = request.pathVariable("topic");
        ServerWebExchange exchange = request.exchange();
        String remote = exchange.getRequest().getRemoteAddress() != null ? exchange.getRequest().getRemoteAddress().toString() : "unknown";

        // Extract Last-Event-ID: header first, then query param fallback
        String lastEventId = exchange.getRequest().getHeaders().getFirst("Last-Event-ID");
        if (lastEventId == null || lastEventId.isBlank()) {
            lastEventId = exchange.getRequest().getQueryParams().getFirst(props.getStream().getLastEventIdParamName());
        }
        final String resolvedLastEventId = lastEventId;

        // Extract authenticated principal from SecurityContext
        Mono<String> principalMono = exchange.getPrincipal()
                .map(java.security.Principal::getName)
                .defaultIfEmpty("");

        return principalMono.flatMap(principal -> exchange.getSession()
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

                    // Build the core behavior supplier
                    Supplier<Flux<ServerSentEvent<Object>>> core = () -> buildFlux(sessionId, topic, remote, exchange, principal, resolvedLastEventId);

                    // Wrap with endpoint customizers (outermost first)
                    Supplier<Flux<ServerSentEvent<Object>>> chain = core;
                    for (int i = endpointCustomizers.size() - 1; i >= 0; i--) {
                        SseEndpointCustomizer customizer = endpointCustomizers.get(i);
                        Supplier<Flux<ServerSentEvent<Object>>> next = chain;
                        chain = () -> customizer.handle(topic, exchange, next);
                    }

                    Flux<ServerSentEvent<Object>> flux = chain.get();

                    // Apply headers inside the response builder so they aren't
                    // overwritten by ServerResponse.ok() (fixes header timing issue).
                    return ServerResponse.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .headers(httpHeaders -> {
                                headerHandler.applyResponseHeaders(exchange);
                                for (SseHeaderCustomizer c : headerCustomizers) {
                                    try {
                                        c.customize(exchange, httpHeaders);
                                    } catch (Throwable t) {
                                        log.warn("Header customizer {} failed: {}", c.getClass().getSimpleName(), t.toString());
                                    }
                                }
                            })
                            .body(flux, ServerSentEvent.class);
                }));
    }

    /**
     * Build the {@link Flux} of {@link ServerSentEvent} for a resolved session/topic.
     * <p>
     * Delegates core stream construction (lifecycle events, retry, error mapping,
     * MDC context enrichment, and stream customizers) to {@link SseStreamOrchestrator}.
     *
     * @param sessionId   resolved or generated session identifier
     * @param topic       the topic path variable
     * @param remote      textual representation of the remote address
     * @param exchange    the current {@link ServerWebExchange}
     * @param principal   the authenticated principal name (empty string if anonymous)
     * @param lastEventId the Last-Event-ID header value, if any
     * @return a composed {@link Flux} of {@link ServerSentEvent} items
     */
    private Flux<ServerSentEvent<Object>> buildFlux(String sessionId, String topic, String remote, ServerWebExchange exchange, String principal, String lastEventId) {
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        SseSession session = SseSession.builder()
                .sessionId(sessionId)
                .topic(topic)
                .principal(principal != null && !principal.isBlank() ? principal : null)
                .lastEventId(lastEventId)
                .remoteAddress(remote)
                .userAgent(userAgent)
                .build();

        return orchestrator.buildStream(session, exchange);
    }
}

