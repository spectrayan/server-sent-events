package com.spectrayan.sse.server.error;

import java.util.Map;

/**
 * Thrown when a sink rejects an emission due to its state (terminated, overflow, etc.).
 */
public class EmissionRejectedException extends SseException {
    public EmissionRejectedException(String topic, String reason, Map<String, Object> details) {
        super(ErrorCode.EMISSION_REJECTED, "Emission rejected: " + reason, topic, details, null);
    }
}
