package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.error.EmissionRejectedException;
import com.spectrayan.sse.server.error.InvalidTopicException;
import com.spectrayan.sse.server.error.TopicNotFoundException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SignalType;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.spectrayan.sse.server.session.SseSession;

/**
 * Server-Sent Events (SSE) topic emitter.
 * <p>
 * This service manages a set of per-topic reactive sinks (one sink per topic) and exposes
 * reactive streams (`Flux`) for clients to subscribe to via {@link #connect(String)} or
 * {@link #connect(String, com.spectrayan.sse.server.session.SseSession)}.
 * Producers can push arbitrary/complex payload objects to specific topics (or broadcast to all)
 * using the various {@code emit*} methods. The emitter takes care of building
 * {@link ServerSentEvent} instances so callers can send any object type without dealing
 * with SSE formatting in controllers.
 * <p>
 * Session id behavior:
 * - When using {@link #connect(String)} (convenience/programmatic path), a new session id is generated
 *   using the configured {@code SessionIdGenerator} (or a UUID fallback if none).
 * - When using {@link #connect(String, com.spectrayan.sse.server.session.SseSession)}, the provided
 *   {@link com.spectrayan.sse.server.session.SseSession} is used as-is and its id is not modified.
 *   This is the path used by the HTTP endpoint handler after it has already decided on the id.
 * <p>
 * Key behaviors:
 * - Topics are created lazily the first time a client connects or an event is emitted for them.
 * - A lightweight heartbeat event with {@code event="heartbeat"} and data {@code "::heartbeat::"}
 *   is merged into each topic stream every 15 seconds while the stream is active, helping proxies
 *   and clients keep the connection alive.
 * - Topics remain active in memory even if the last subscriber disconnects. The server keeps
 *   connections/topics alive and will only complete and remove a topic when a client cancels the
 *   subscription, an error occurs, or the application terminates (graceful shutdown).
 */
public abstract class AbstractSseEmitter implements SseEmitter, com.spectrayan.sse.server.topic.TopicRegistry {

    private static final Logger log = LoggerFactory.getLogger(AbstractSseEmitter.class);

    private final SseServerProperties properties;
    private final com.spectrayan.sse.server.customize.SseEmitterCustomizer sinkCustomizer;
    private final java.util.List<com.spectrayan.sse.server.customize.SseSessionHook> sessionHooks;
    private final com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator;

    // SRP components
    private final TopicValidator topicValidator;
    private final SinkFactory sinkFactory;
    private final TopicManager topicManager;
    private final StreamComposer streamComposer;
    private final SessionTracker sessionTracker;
    private final EmissionService emissionService;

    public AbstractSseEmitter(SseServerProperties properties,
                              org.springframework.beans.factory.ObjectProvider<com.spectrayan.sse.server.customize.SseEmitterCustomizer> sinkCustomizer,
                              org.springframework.beans.factory.ObjectProvider<com.spectrayan.sse.server.customize.SseSessionHook> sessionHooks,
                              com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator) {
        this.properties = properties;
        this.sinkCustomizer = sinkCustomizer != null ? sinkCustomizer.getIfAvailable() : null;
        this.sessionHooks = sessionHooks != null ? sessionHooks.orderedStream().toList() : java.util.List.of();
        this.sessionIdGenerator = sessionIdGenerator;
        this.topicValidator = new TopicValidator(properties);
        this.sinkFactory = new SinkFactory(properties, this.sinkCustomizer);
        this.topicManager = new TopicManager(this.sinkFactory);
        this.streamComposer = new StreamComposer(properties);
        this.sessionTracker = new SessionTracker(this.sessionHooks, this.topicManager);
        this.emissionService = new EmissionService();
    }



    // TopicRegistry implementation
    @Override
    public java.util.Collection<String> topics() {
        return topicManager.topics();
    }

    @Override
    public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) {
        return topicManager.sessions(topic);
    }

    @Override
    public int subscriberCount(String topic) {
        return topicManager.subscriberCount(topic);
    }

    @Override
    public java.util.Map<String, Integer> topicSubscriberCounts() {
        return topicManager.topicSubscriberCounts();
    }

    /**
     * Connect to a topic and receive a live stream of {@link ServerSentEvent} items.
     * <p>
     * This is a convenience/programmatic overload used when you don't have an existing
     * {@link com.spectrayan.sse.server.session.SseSession}. It will create a new session internally
     * and generate a session id via the configured {@code SessionIdGenerator} (or a UUID fallback
     * if none is configured).
     * <p>
     * The topic channel is created on first access. A periodic heartbeat event is merged into the
     * stream (every ~15s) to keep connections alive. When the last subscriber disconnects, the topic
     * sink is completed and removed.
     * <p>
     * If you already determined a session id (e.g., in an HTTP endpoint), prefer
     * {@link #connect(String, com.spectrayan.sse.server.session.SseSession)} so the provided id is
     * preserved.
     *
     * @param topic the topic identifier (path segment used by clients to subscribe)
     * @return a hot {@link Flux} of {@link ServerSentEvent} carrying objects previously emitted to the topic
     */
    public Flux<ServerSentEvent<Object>> connect(String topic) {
        String id = sessionIdGenerator != null ? sessionIdGenerator.generate(null, topic) : java.util.UUID.randomUUID().toString();
        com.spectrayan.sse.server.session.SseSession session = com.spectrayan.sse.server.session.SseSession.builder()
                .sessionId(id)
                .topic(topic)
                .build();
        return connect(topic, session);
    }

    /**
     * Connect to a topic using an already constructed {@link com.spectrayan.sse.server.session.SseSession}.
     * <p>
     * The provided session (including its {@code sessionId}) is used as-is; this method does not
     * generate or override the id. This is the path used by the HTTP endpoint handler, which decides
     * the id based on the WebSession id or the configured {@code SessionIdGenerator}.
     * <p>
     * The topic channel is created on first access. A periodic heartbeat event is merged into the
     * stream (every ~15s) to keep connections alive. When the last subscriber disconnects, the topic
     * sink is completed and removed.
     *
     * @param topic   the topic identifier
     * @param session the session metadata to track for this subscription; its id is preserved
     * @return a hot {@link Flux} of {@link ServerSentEvent}
     */
    @Override
    public Flux<ServerSentEvent<Object>> connect(String topic, SseSession session) {
        validateTopicOrThrow(topic);
        TopicChannel channel = topicManager.getOrCreate(topic);

        // Enforce max subscribers if configured
        int max = properties.getTopics().getMaxSubscribers();
        return Flux.defer(() -> {
            if (max > 0 && channel.subscribers.get() >= max) {
                throw new com.spectrayan.sse.server.error.SseException(
                        com.spectrayan.sse.server.error.ErrorCode.SUBSCRIPTION_REJECTED,
                        "Max subscribers exceeded for topic " + topic,
                        topic
                );
            }

            Flux<ServerSentEvent<Object>> sinkFlux = channel.sink.asFlux();
            Flux<ServerSentEvent<Object>> merged = streamComposer.compose(topic, sinkFlux);
            return sessionTracker.decorate(topic, merged, channel, session);
        });
    }

    /**
     * Emit a payload to a specific topic.
     * <p>
     * If the topic does not exist or has no active subscribers, the payload is ignored.
     * The payload can be any serializable object; it will be wrapped into a {@link ServerSentEvent}
     * without an explicit event name or id.
     *
     * @param topicId the target topic id
     * @param payload the payload object to send (any type supported by your HTTP message converters)
     * @param <T>     the payload type
     */
    public <T> void emitToTopic(String topicId, T payload) {
        validateTopicOrThrow(topicId);
        emissionService.emitToTopic(topicManager, topicId, null, payload, null);
    }

    /**
     * Emit a payload with a custom SSE event name to a specific topic.
     *
     * @param topicId   the target topic id
     * @param eventName the SSE event name to set (e.g. "orderCreated"); may be {@code null}
     * @param payload   the payload object to send
     * @param <T>       the payload type
     */
    public <T> void emitToTopic(String topicId, String eventName, T payload) {
        validateTopicOrThrow(topicId);
        emissionService.emitToTopic(topicManager, topicId, eventName, payload, null);
    }

    /**
     * Emit a payload with a custom SSE event name and id to a specific topic.
     * <p>
     * If the topic channel does not exist, nothing happens.
     *
     * @param topicId   the target topic id
     * @param eventName the SSE event name to set; may be {@code null}
     * @param payload   the payload object to send
     * @param id        optional SSE id (used by clients for reconnection via Last-Event-ID); may be {@code null}
     * @param <T>       the payload type
     */
    public <T> void emitToTopic(String topicId, String eventName, T payload, String id) {
        validateTopicOrThrow(topicId);
        emissionService.emitToTopic(topicManager, topicId, eventName, payload, id);
    }

    private String describePayload(Object payload) {
        if (payload == null) return "null";
        String type = payload.getClass().getSimpleName();
        int hash = System.identityHashCode(payload);
        return switch (payload) {
            case CharSequence cs -> type + "[len=" + cs.length() + "]#" + hash;
            case byte[] bytes -> type + "[len=" + bytes.length + "]#" + hash;
            case Collection<?> col -> type + "[size=" + col.size() + "]#" + hash;
            case Map<?, ?> map -> type + "[size=" + map.size() + "]#" + hash;
            default -> type + "#" + hash;
        };
    }

    private void validateTopicOrThrow(String topic) {
        topicValidator.validateOrThrow(topic);
    }

    private EmissionRejectedException mapEmitFailure(String topic, Sinks.EmitResult result, String eventName, String id) {
        String safeEventName = eventName != null ? eventName : "";
        String safeId = id != null ? id : "";
        String emitResultName = (result != null) ? result.name() : "NULL";
        log.warn("Failed to emit to topic {} result={} eventName={} id={}", topic, emitResultName, safeEventName, safeId);
        return new EmissionRejectedException(topic, emitResultName, Map.of(
                "emitResult", emitResultName,
                "eventName", safeEventName,
                "id", safeId
        ));
    }

    /**
     * Broadcast a payload to all currently active topics.
     *
     * @param payload the payload object to send to every topic
     * @param <T>     the payload type
     */
    public <T> void emitToAll(T payload) {
        emissionService.broadcast(topicManager, payload);
    }

    /**
     * Get the identifiers of topics that currently have an active channel in memory.
     * <p>
     * Note: this is a snapshot view of the backing map keys and may change immediately after return.
     *
     * @return a collection of active topic ids
     */
    public Collection<String> currentTopics() {
        return topicManager.topics();
    }

    /**
     * Convenience alias: emit a payload to a topic.
     * <p>
     * Equivalent to {@link #emitToTopic(String, Object)}.
     *
     * @param topicId the target topic id
     * @param payload the payload object to send
     * @param <T>     the payload type
     */
    public <T> void emit(String topicId, T payload) {
        emitToTopic(topicId, payload);
    }

    /**
     * Convenience alias: emit a payload with event name to a topic.
     * <p>
     * Equivalent to {@link #emitToTopic(String, String, Object)}.
     *
     * @param topicId   the target topic id
     * @param eventName the SSE event name
     * @param payload   the payload object to send
     * @param <T>       the payload type
     */
    public <T> void emit(String topicId, String eventName, T payload) {
        emitToTopic(topicId, eventName, payload);
    }

    /**
     * Convenience alias: emit a payload with event name and id to a topic.
     * <p>
     * Equivalent to {@link #emitToTopic(String, String, Object, String)}.
     *
     * @param topicId   the target topic id
     * @param eventName the SSE event name
     * @param payload   the payload object to send
     * @param id        optional SSE id
     * @param <T>       the payload type
     */
    public <T> void emit(String topicId, String eventName, T payload, String id) {
        emitToTopic(topicId, eventName, payload, id);
    }

    /**
     * Broadcast alias: emit to all connected topics.
     * <p>
     * Equivalent to {@link #emitToAll(Object)}.
     *
     * @param payload the payload object to broadcast to all topics
     * @param <T>     the payload type
     */
    public <T> void emit(T payload) {
        emitToAll(payload);
    }

    /**
     * Gracefully close all active SSE channels on application shutdown to avoid
     * blocking graceful shutdown with in-flight requests.
     * <p>
     * This completes each sink and clears the topic registry. Any subsequent attempt to connect
     * will recreate topics on demand.
     */
    @PreDestroy
    public void shutdown() {
        topicManager.shutdownAll();
    }
}
