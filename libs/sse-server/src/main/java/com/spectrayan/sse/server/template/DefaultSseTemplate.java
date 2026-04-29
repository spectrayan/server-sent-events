package com.spectrayan.sse.server.template;

import com.spectrayan.sse.server.config.SseHeaderHandler;
import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.controller.SseStreamOrchestrator;
import com.spectrayan.sse.server.customize.SseEndpointCustomizer;
import com.spectrayan.sse.server.customize.SseHeaderCustomizer;
import com.spectrayan.sse.server.customize.SseStreamCustomizer;
import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.error.ErrorEvents;
import com.spectrayan.sse.server.error.SseException;
import com.spectrayan.sse.server.session.SseSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Supplier;

/**
 * Default implementation of {@link SseTemplate} delegating to {@link SseEmitter}.
 *
 * This component centralizes the same orchestration present in {@code SseEndpointHandler}
 * so applications can inject a higher-level template API while hiding lower-level details.
 */
public class DefaultSseTemplate implements SseTemplate {

    private static final Logger log = LoggerFactory.getLogger(DefaultSseTemplate.class);

    private final SseEmitter emitter;
    private final SseHeaderHandler headerHandler;
    private final SseServerProperties props;
    private final List<SseStreamCustomizer> streamCustomizers;
    private final List<SseHeaderCustomizer> headerCustomizers;
    private final List<SseEndpointCustomizer> endpointCustomizers;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator;
    // Strategy hooks
    private final EventSerializer serializer;
    private final ClientFilter clientFilter;
    private final ReconnectPolicy reconnectPolicy;
    private final HeartbeatPolicy heartbeatPolicy;
    private final ErrorMapper errorMapper;
    private final ConnectionRegistry connectionRegistry;
    private final SseStreamOrchestrator orchestrator;

    /**
     * Create the default {@link SseTemplate} implementation.
     *
     * @param emitter low-level emitter used to connect/broadcast SSEs
     * @param headerHandler helper to apply standard SSE response headers
     * @param props configuration properties controlling behavior
     * @param streamCustomizers optional ordered customizers for per-request stream transformation
     * @param headerCustomizers optional ordered customizers for response headers
     * @param endpointCustomizers optional ordered customizers that can wrap endpoint handling
     * @param eventPublisher Spring publisher for session lifecycle events
     * @param sessionIdGenerator strategy to produce session identifiers
     * @param serializer strategy to serialize payloads into {@link org.springframework.http.codec.ServerSentEvent}
     * @param clientFilter filter to allow/deny incoming connections
     * @param reconnectPolicy policy controlling SSE retry advertisement
     * @param heartbeatPolicy policy producing heartbeat events
     * @param errorMapper mapper to convert stream errors to SSE frames
     * @param connectionRegistry registry exposing topic/session introspection
     */
    public DefaultSseTemplate(SseEmitter emitter,
                       SseHeaderHandler headerHandler,
                       SseServerProperties props,
                       ObjectProvider<SseStreamCustomizer> streamCustomizers,
                       ObjectProvider<SseHeaderCustomizer> headerCustomizers,
                       ObjectProvider<SseEndpointCustomizer> endpointCustomizers,
                       org.springframework.context.ApplicationEventPublisher eventPublisher,
                       com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator,
                       EventSerializer serializer,
                       ClientFilter clientFilter,
                       ReconnectPolicy reconnectPolicy,
                       HeartbeatPolicy heartbeatPolicy,
                       ErrorMapper errorMapper,
                       ConnectionRegistry connectionRegistry) {
        this.emitter = emitter;
        this.headerHandler = headerHandler;
        this.props = props;
        this.streamCustomizers = streamCustomizers != null ? streamCustomizers.orderedStream().toList() : List.of();
        this.headerCustomizers = headerCustomizers != null ? headerCustomizers.orderedStream().toList() : List.of();
        this.endpointCustomizers = endpointCustomizers != null ? endpointCustomizers.orderedStream().toList() : List.of();
        this.eventPublisher = eventPublisher;
        this.sessionIdGenerator = sessionIdGenerator;
        this.serializer = serializer;
        this.clientFilter = clientFilter;
        this.reconnectPolicy = reconnectPolicy;
        this.heartbeatPolicy = heartbeatPolicy;
        this.errorMapper = errorMapper;
        this.connectionRegistry = connectionRegistry;
        this.orchestrator = new SseStreamOrchestrator(emitter, props, eventPublisher, this.streamCustomizers);
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        ServerWebExchange exchange = request.exchange();
        String topic = request.pathVariable("topic");
        String remote = (exchange != null && exchange.getRequest() != null && exchange.getRequest().getRemoteAddress() != null)
                ? exchange.getRequest().getRemoteAddress().toString() : "";

        Mono<String> sessionIdMono = (exchange != null
                ? exchange.getSession().map(WebSession::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .switchIfEmpty(Mono.fromSupplier(() -> sessionIdGenerator != null ? sessionIdGenerator.generate(exchange, topic) : java.util.UUID.randomUUID().toString()))
                : Mono.fromSupplier(() -> sessionIdGenerator != null ? sessionIdGenerator.generate(exchange, topic) : java.util.UUID.randomUUID().toString()));
        return sessionIdMono
                .flatMap(sid -> {

                    try {
                        eventPublisher.publishEvent(new com.spectrayan.sse.server.events.SseSessionCreatedEvent(sid, topic, remote));
                    } catch (Throwable t) {
                        log.debug("Failed to publish SseSessionCreatedEvent: {}", t.toString());
                    }

                    // Build connect context
                    String lastEventId = exchange != null && exchange.getRequest() != null
                            ? exchange.getRequest().getHeaders().getFirst("Last-Event-ID") : null;
                    java.util.Map<String, String> headers = exchange != null && exchange.getRequest() != null
                            ? exchange.getRequest().getHeaders().toSingleValueMap() : java.util.Map.of();
                    SseConnectContext ctx = new SseConnectContext(topic, sid, lastEventId, remote, headers, java.util.Map.of());

                    Supplier<Flux<ServerSentEvent<Object>>> core = () -> connect(topic, ctx);

                    // Wrap with endpoint customizers (outermost first)
                    Supplier<Flux<ServerSentEvent<Object>>> chain = core;
                    for (int i = endpointCustomizers.size() - 1; i >= 0; i--) {
                        SseEndpointCustomizer customizer = endpointCustomizers.get(i);
                        Supplier<Flux<ServerSentEvent<Object>>> next = chain;
                        chain = () -> customizer.handle(topic, exchange, next);
                    }

                    Flux<ServerSentEvent<Object>> flux = chain.get();
                    // Apply configured response headers and customizers
                    if (exchange != null) {
                        try {
                            headerHandler.applyResponseHeaders(exchange);
                            for (SseHeaderCustomizer c : headerCustomizers) {
                                try { c.customize(exchange, exchange.getResponse().getHeaders()); }
                                catch (Throwable t) { log.warn("Header customizer {} failed: {}", c.getClass().getSimpleName(), t.toString()); }
                            }
                        } catch (Throwable ignored) {}
                    }

                    return ServerResponse.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(flux, ServerSentEvent.class);
                });
    }

    @Override
    public Flux<ServerSentEvent<Object>> connect(String topic, SseConnectContext ctx) {
        String remote = ctx.remoteAddress();
        String sessionId = ctx.sessionId();
        log.info("SSE subscription requested (template): topic={} from {}", topic, remote);

        // Client filtering
        if (clientFilter != null) {
            try {
                if (!clientFilter.allow(ctx)) {
                    return Flux.error(new com.spectrayan.sse.server.error.SseException(
                            com.spectrayan.sse.server.error.ErrorCode.SUBSCRIPTION_REJECTED,
                            "SSE client rejected",
                            topic
                    ));
                }
            } catch (Throwable t) {
                return Flux.error(t);
            }
        }

        // Build SseSession from context
        SseSession session = SseSession.builder()
                .sessionId(sessionId)
                .topic(topic)
                .remoteAddress(remote)
                .userAgent(ctx.requestHeaders() != null ? ctx.requestHeaders().get("User-Agent") : null)
                .build();

        Flux<ServerSentEvent<Object>> core = emitter.connect(topic, session)
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

        // retry policy
        if (reconnectPolicy != null) {
            java.util.Optional<Long> delay = reconnectPolicy.retryDelayMillis(ctx);
            if (delay.isPresent()) {
                core = Flux.concat(Flux.just(ServerSentEvent.<Object>builder().retry(java.time.Duration.ofMillis(delay.get())).build()), core);
            }
        } else if (props.getStream().isRetryEnabled()) {
            core = Flux.concat(Flux.just(ServerSentEvent.<Object>builder().retry(props.getStream().getRetry()).build()), core);
        }

        // heartbeats
        if (heartbeatPolicy != null) {
            Flux<ServerSentEvent<Object>> heartbeats = heartbeatPolicy.heartbeats(ctx);
            if (heartbeats != null) {
                core = core.mergeWith(heartbeats);
            }
        }

        // error mapping
        if (props.getStream().isMapErrorsToSse()) {
            core = core
                    .doOnError(ex -> {
                        log.warn("SSE stream error: topic={} from {} error={}", topic, remote, ex.toString());
                        try {
                            eventPublisher.publishEvent(new com.spectrayan.sse.server.events.SseDisconnectedEvent(sessionId, topic, remote, ex));
                        } catch (Throwable t) {
                            log.debug("Failed to publish SseDisconnectedEvent: {}", t.toString());
                        }
                    })
                    .onErrorResume(ex -> errorMapper != null ? errorMapper.map(ex, ctx) : Mono.deferContextual(view -> {
                        if (ex instanceof SseException se) return Mono.just(ErrorEvents.fromException(se, topic, view));
                        return Mono.just(ErrorEvents.fromThrowable(ex, topic, view));
                    }));
        }

        // Apply stream customizers (no exchange available here)
        for (SseStreamCustomizer c : streamCustomizers) {
            try {
                core = c.customize(topic, null, core);
            } catch (Throwable t) {
                log.warn("Stream customizer {} failed: {}", c.getClass().getSimpleName(), t.toString());
            }
        }

        return core;
    }

    @Override
    public <T> void send(String topicId, T payload) {
        emitter.emit(topicId, payload);
    }

    @Override
    public <T> void send(String topicId, String eventName, T payload) {
        emitter.emit(topicId, eventName, payload);
    }

    @Override
    public <T> void send(String topicId, String eventName, T payload, String id) {
        emitter.emit(topicId, eventName, payload, id);
    }

    @Override
    public <T> void broadcast(T payload) {
        emitter.emit(payload);
    }

    @Override
    public ConnectionRegistry registry() {
        return connectionRegistry;
    }
}
