package com.spectrayan.sse.server.error;

import java.time.Instant;
import java.util.Map;

/**
 * Structured error payload sent to SSE clients on failures.
 */
public record ErrorPayload(
        String code,
        String message,
        String topic,
        Instant timestamp,
        Map<String, Object> details
) {
}
