package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.session.SseSession;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Collection;

/**
 * Public API for Server‑Sent Events (SSE) topics: connecting subscribers and emitting events.
 * <p>
 * Concepts:
 * - Topic: a logical channel identified by a string (e.g., path variable). Each topic has a hot sink.
 * - Subscriber connection: a {@link Flux} of {@link ServerSentEvent} that remains active until
 *   cancelled or the topic completes.
 * - Emission: pushing an event (with optional {@code event} and {@code id} fields) into one topic or all topics.
 * <p>
 * Implementations are expected to be thread‑safe.
 */
public interface SseEmitter {

    /**
     * Connect to a topic and receive the stream of events.
     * <p>
     * This variant does not provide session metadata. Implementations should still admit the
     * subscription and keep internal counts.
     *
     * @param topic the topic identifier to subscribe to (must not be null/blank)
     * @return a hot {@link Flux} that emits SSEs for the specified topic; completes when the topic is removed
     */
    Flux<ServerSentEvent<Object>> connect(String topic);

    /**
     * Connect to a topic with an associated {@link SseSession} carrying client metadata.
     * The session may be recorded in per-topic session maps and used by hooks.
     *
     * @param topic the topic identifier to subscribe to
     * @param session optional session metadata describing the subscriber; may be {@code null}
     * @return a hot {@link Flux} of SSEs for the topic
     */
    Flux<ServerSentEvent<Object>> connect(String topic, SseSession session);

    /**
     * Emit a data-only SSE to a specific topic. Equivalent to {@link #emit(String, Object)}.
     *
     * @param topicId topic to emit to
     * @param payload payload to serialize into {@code data}
     * @throws com.spectrayan.sse.server.error.TopicNotFoundException if the topic does not exist yet
     * @throws com.spectrayan.sse.server.error.EmissionRejectedException if Reactor sink rejects the signal
     */
    <T> void emitToTopic(String topicId, T payload);

    /**
     * Emit to a specific topic with an explicit {@code event} name.
     *
     * @param topicId topic to emit to
     * @param eventName the SSE {@code event} name (may be {@code null})
     * @param payload payload to send
     * @throws com.spectrayan.sse.server.error.TopicNotFoundException if the topic is unknown
     * @throws com.spectrayan.sse.server.error.EmissionRejectedException if the sink rejects the emission
     */
    <T> void emitToTopic(String topicId, String eventName, T payload);

    /**
     * Emit to a specific topic with {@code event} and {@code id} fields.
     *
     * @param topicId topic to emit to
     * @param eventName event name (nullable)
     * @param payload payload to send
     * @param id SSE {@code id} to set (nullable)
     * @throws com.spectrayan.sse.server.error.TopicNotFoundException if the topic is unknown
     * @throws com.spectrayan.sse.server.error.EmissionRejectedException if the sink rejects the emission
     */
    <T> void emitToTopic(String topicId, String eventName, T payload, String id);

    /**
     * Broadcast a data-only event to all currently active topics. Best‑effort: topics that
     * reject the signal are logged and skipped; the method does not fail for other topics.
     *
     * @param payload payload to send to all topics
     */
    <T> void emitToAll(T payload);

    /**
     * Return the identifiers of currently active topics.
     */
    Collection<String> currentTopics();

    /**
     * Synonym for {@link #emitToTopic(String, Object)}.
     */
    <T> void emit(String topicId, T payload);

    /**
     * Synonym for {@link #emitToTopic(String, String, Object)}.
     */
    <T> void emit(String topicId, String eventName, T payload);

    /**
     * Synonym for {@link #emitToTopic(String, String, Object, String)}.
     */
    <T> void emit(String topicId, String eventName, T payload, String id);

    /**
     * Broadcast to all topics. Synonym for {@link #emitToAll(Object)}.
     */
    <T> void emit(T payload);

    /**
     * Shut down the emitter, completing all topic sinks and releasing resources.
     * After shutdown, further emissions are no-ops or rejected depending on implementation.
     */
    void shutdown();
}
