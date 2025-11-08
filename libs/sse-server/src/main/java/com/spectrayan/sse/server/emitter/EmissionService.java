package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.error.EmissionRejectedException;
import com.spectrayan.sse.server.error.TopicNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.util.Collection;
import java.util.Map;

/**
 * Encapsulates building and emitting of {@link org.springframework.http.codec.ServerSentEvent} instances
 * to specific topics and broadcasting to all active topics.
 * <p>
 * Responsibilities:
 * - Convert arbitrary payload + optional event name/id into {@link ServerSentEvent} and emit via topic sink.
 * - Map Reactor {@link reactor.core.publisher.Sinks.EmitResult} failures to a domain-specific
 *   {@link com.spectrayan.sse.server.error.EmissionRejectedException} with structured details.
 * - Broadcast a single pre-built event to all active topics, logging per-topic rejections without failing the call.
 * <p>
 * Package-private and used by {@link AbstractSseEmitter} to separate emission concerns from orchestration.
 */
final class EmissionService {

    private static final Logger log = LoggerFactory.getLogger(EmissionService.class);

    /**
     * Emit a single {@link ServerSentEvent} to a specific topic.
     * <p>
     * The event is constructed from the provided {@code payload}, optional {@code eventName}, and optional {@code id}.
     * The emission is performed using the topic's Reactor {@link Sinks.Many} sink with {@code tryEmitNext} to avoid
     * blocking. On failure, the {@link Sinks.EmitResult} is mapped to a domain specific
     * {@link com.spectrayan.sse.server.error.EmissionRejectedException}.
     *
     * @param topicManager access to topic channels
     * @param topicId the target topic identifier
     * @param eventName optional SSE {@code event} name; may be {@code null}
     * @param payload event data; may be any object supported by the configured encoders
     * @param id optional SSE {@code id}; may be {@code null}
     * @throws com.spectrayan.sse.server.error.TopicNotFoundException when the topic was not created/active
     * @throws com.spectrayan.sse.server.error.EmissionRejectedException when the Reactor sink rejects the signal
     */
    void emitToTopic(TopicManager topicManager, String topicId, String eventName, Object payload, String id) {
        TopicChannel channel = topicManager.get(topicId);
        if (channel == null) {
            throw new TopicNotFoundException(topicId);
        }
        ServerSentEvent.Builder<Object> builder = ServerSentEvent.<Object>builder((Object) payload);
        if (eventName != null) builder.event(eventName);
        if (id != null) builder.id(id);
        if (log.isDebugEnabled()) {
            log.debug("Emitting to topic {} eventName={} id={} payload={}", topicId, eventName, id, describePayload(payload));
        }
        Sinks.EmitResult result = channel.sink.tryEmitNext(builder.build());
        if (result.isFailure()) {
            throw mapEmitFailure(topicId, result, eventName, id);
        }
    }

/**
     * Broadcast a single event to all currently active topics.
     * <p>
     * A single {@link ServerSentEvent} instance is created once and offered to each topic's sink using
     * {@code tryEmitNext}. This is best‑effort: any individual topic rejection is logged at WARN level,
     * but does not prevent attempts for the remaining topics.
     *
     * @param topicManager access to topic channels
     * @param payload event payload to broadcast
     */
    void broadcast(TopicManager topicManager, Object payload) {
        Collection<String> ids = topicManager.topics();
        int count = ids.size();
        if (count == 0) {
            log.warn("No active topics to broadcast to; payload ignored");
            return;
        }
        ServerSentEvent<Object> event = ServerSentEvent.<Object>builder((Object) payload).build();
        if (log.isDebugEnabled()) {
            log.debug("Broadcasting to {} topic(s) payload={}", count, describePayload(payload));
        }
        for (String id : ids) {
            TopicChannel ch = topicManager.get(id);
            if (ch == null) continue;
            Sinks.EmitResult res = ch.sink.tryEmitNext(event);
            if (res.isFailure()) {
                log.warn("Broadcast emit rejected for topic {} result={}", id, res);
            }
        }
    }

    /**
     * Produce a concise, safe textual description of a payload for logs.
     * <p>
     * Includes simple type name, identity hash, and size/length hints for common containers
     * (CharSequence, byte[], Collection, Map). Avoids printing full payload content.
     *
     * @param payload payload to describe
     * @return human-friendly short description, never {@code null}
     */
    private String describePayload(Object payload) {
        if (payload == null) return "null";
        String type = payload.getClass().getSimpleName();
        int hash = System.identityHashCode(payload);
        if (payload instanceof CharSequence cs) return type + "[len=" + cs.length() + "]#" + hash;
        if (payload instanceof byte[] bytes) return type + "[len=" + bytes.length + "]#" + hash;
        if (payload instanceof java.util.Collection<?> col) return type + "[size=" + col.size() + "]#" + hash;
        if (payload instanceof Map<?, ?> map) return type + "[size=" + map.size() + "]#" + hash;
        return type + "#" + hash;
    }

    /**
     * Map a Reactor {@link Sinks.EmitResult} failure into an {@link com.spectrayan.sse.server.error.EmissionRejectedException}
     * with structured details suitable for clients and logs.
     *
     * @param topic the topic being emitted to
     * @param result the failed {@link Sinks.EmitResult} (may be {@code null})
     * @param eventName the SSE {@code event} name used (nullable)
     * @param id the SSE {@code id} used (nullable)
     * @return an {@link com.spectrayan.sse.server.error.EmissionRejectedException} describing the failure
     */
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
}
