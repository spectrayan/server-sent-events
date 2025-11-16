package com.spectrayan.sse.server.error;

import java.time.Instant;
import java.util.Map;

/**
 * Structured error payload sent to SSE clients on failures.
 *
 * @param code stable error code (e.g., BUSINESS_RULE_VIOLATION)
 * @param message human‑readable message suitable for client logs
 * @param topic the topic associated with the failure, if known
 * @param timestamp the instant when the error was produced
 * @param details arbitrary structured key/value details for diagnostics
 */
public record ErrorPayload(
        String code,
        String message,
        String topic,
        Instant timestamp,
        Map<String, Object> details
) {
}
