package com.spectrayan.sse.server.bridge;

import java.io.Serializable;

/**
 * Envelope for an SSE event broadcast across instances.
 * <p>
 * Carries enough data to reconstruct a
 * {@link org.springframework.http.codec.ServerSentEvent} on the receiving side.
 * The {@code payload} field uses {@link Object} to align with the existing
 * {@link com.spectrayan.sse.server.emitter.SseEmitter} API, which also works
 * with arbitrary payload types. Serialization to/from the wire format (JSON,
 * Avro, Protobuf, etc.) is handled by the bridge implementation or the
 * underlying messaging infrastructure (e.g., Spring Cloud Stream content-type
 * negotiation).
 * <p>
 * Implements {@link Serializable} as a convenience for bridge implementations
 * that use Java serialization, though JSON serialization via Jackson is the
 * recommended default.
 *
 * @param originInstanceId unique identifier of the instance that emitted the event,
 *                         used by receivers to skip re-injection of their own events
 * @param topic            SSE topic the event targets; never {@code null}
 * @param eventName        optional SSE {@code event} name; may be {@code null}
 * @param payload          event data — any object supported by the configured serializers
 * @param id               optional SSE {@code id} for Last-Event-ID tracking; may be {@code null}
 * @param timestamp        epoch millis when the event was created on the originating instance
 * @since 2.0.0
 */
public record SseBridgeMessage(
        String originInstanceId,
        String topic,
        String eventName,
        Object payload,
        String id,
        long timestamp
) implements Serializable {}
