package com.spectrayan.sse.server.error;

import java.util.Map;

/**
 * Thrown when a sink rejects an emission due to its state (terminated, overflow, etc.).
 */
public class EmissionRejectedException extends SseException {
    /**
     * Create an exception indicating that an emission to a topic was rejected by the sink.
     *
     * @param topic the topic the emission targeted
     * @param reason human-readable reason (e.g., "TERMINATED", "OVERFLOW")
     * @param details additional structured details, may be empty but never used to control flow
     */
    public EmissionRejectedException(String topic, String reason, Map<String, Object> details) {
        super(ErrorCode.EMISSION_REJECTED, "Emission rejected: " + reason, topic, details, null);
    }
}
