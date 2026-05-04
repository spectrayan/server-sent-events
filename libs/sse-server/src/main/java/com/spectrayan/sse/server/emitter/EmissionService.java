package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.bridge.SseBridgeMessage;
import com.spectrayan.sse.server.config.SseServerProperties;
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

    private final com.spectrayan.sse.server.metrics.SseMetrics metrics;
    private final int maxEmitRetries;
    private final SseBroadcastBridge bridge;
    private final String instanceId;

    /**
     * Create a new EmissionService.
     *
     * @param metrics optional SSE metrics recorder; may be {@code null}
     * @param maxEmitRetries maximum retry attempts on {@code FAIL_NON_SERIALIZED};
     *                       0 to disable retry (fail immediately on contention).
     *                       Clamped to [{@code 0}, {@link SseServerProperties.Emitter#MAX_EMIT_RETRIES}]
     *                       by the properties layer.
     * @param bridge broadcast bridge for cross-instance fan-out; may be {@code null}
     * @param instanceId unique identifier for this instance used in bridge messages; may be {@code null}
     */
    EmissionService(com.spectrayan.sse.server.metrics.SseMetrics metrics, int maxEmitRetries,
                    SseBroadcastBridge bridge, String instanceId) {
        this.metrics = metrics;
        this.maxEmitRetries = maxEmitRetries;
        this.bridge = bridge;
        this.instanceId = instanceId;
    }

    /**
     * Emit a single {@link ServerSentEvent} to a specific topic.
     * <p>
     * The event is constructed from the provided {@code payload}, optional {@code eventName}, and optional {@code id}.
     * The emission uses a two-phase strategy: first attempt with {@code tryEmitNext} for the fast path,
     * and on {@link Sinks.EmitResult#FAIL_NON_SERIALIZED FAIL_NON_SERIALIZED} (concurrent emit contention on
     * serialized sinks such as REPLAY), retries up to the configured {@code emitRetries} times with
     * {@link Thread#onSpinWait()} between attempts. All other failures are mapped to a domain-specific
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
        ServerSentEvent<Object> event = builder.build();
        Sinks.EmitResult result = emitWithSerializationRetry(channel.sink, event, topicId);
        if (result.isFailure()) {
            if (metrics != null) metrics.recordEmitFailure(topicId);
            throw mapEmitFailure(topicId, result, eventName, id);
        }
        if (metrics != null) metrics.recordEmit(topicId);
        // Fan-out to other instances via broadcast bridge
        publishToBridge(topicId, eventName, payload, id);
    }

    /**
     * Broadcast a single event to all currently active topics.
     * <p>
     * A single {@link ServerSentEvent} instance is created once and offered to each topic's sink.
     * This is best‑effort: any individual topic rejection is logged at WARN level,
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
            Sinks.EmitResult res = emitWithSerializationRetry(ch.sink, event, id);
            if (res.isFailure()) {
                log.warn("Broadcast emit rejected for topic {} result={}", id, res);
            } else {
                // Fan-out each topic's broadcast to other instances
                publishToBridge(id, null, payload, null);
            }
        }
    }

    /**
     * Attempt to emit an event to a sink, retrying up to the configured {@code maxEmitRetries} times
     * on {@link Sinks.EmitResult#FAIL_NON_SERIALIZED FAIL_NON_SERIALIZED}.
     * <p>
     * Serialized sinks (e.g. REPLAY) return {@code FAIL_NON_SERIALIZED} when another thread is
     * concurrently emitting. The contention window is typically nanoseconds, so a short spin-retry
     * is the correct strategy. {@link Thread#onSpinWait()} is used between attempts to hint the CPU
     * to optimize for the spin-wait pattern (e.g. reduce power, yield to hyper-threading sibling).
     * <p>
     * If a non-serialization failure occurs during retry (e.g. the sink transitions to
     * {@code FAIL_TERMINATED}), the retry loop exits immediately with that result, ensuring the
     * caller can handle it explicitly rather than silently dropping the event.
     *
     * @param sink the Reactor sink to emit to
     * @param event the SSE event to emit
     * @param topicId topic identifier for logging
     * @return the final {@link Sinks.EmitResult} — either {@code OK} or the failure that exhausted retries
     */
    private Sinks.EmitResult emitWithSerializationRetry(
            Sinks.Many<ServerSentEvent<Object>> sink,
            ServerSentEvent<Object> event,
            String topicId) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result != Sinks.EmitResult.FAIL_NON_SERIALIZED || maxEmitRetries == 0) {
            return result;
        }
        // Serialization contention — bounded retry with spin-wait
        for (int attempt = 1; attempt <= maxEmitRetries; attempt++) {
            Thread.onSpinWait();
            result = sink.tryEmitNext(event);
            if (result != Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                if (attempt > 1 && log.isDebugEnabled()) {
                    log.debug("Serialization contention on topic {} resolved after {} retries", topicId, attempt);
                }
                return result;
            }
        }
        log.warn("Serialization contention on topic {} not resolved after {} retries", topicId, maxEmitRetries);
        return result;
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

    /**
     * Publish an event to the broadcast bridge for cross-instance delivery.
     * <p>
     * Failures are logged at WARN level but never prevent local delivery.
     * This method is a no-op when no bridge is configured.
     */
    private void publishToBridge(String topicId, String eventName, Object payload, String id) {
        if (bridge == null) return;
        try {
            bridge.publish(new SseBridgeMessage(
                    instanceId, topicId, eventName, payload, id,
                    System.currentTimeMillis()));
        } catch (Throwable t) {
            log.warn("Bridge publish failed for topic {}: {}", topicId, t.getMessage());
        }
    }
}
