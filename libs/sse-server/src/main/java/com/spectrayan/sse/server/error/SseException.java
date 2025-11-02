package com.spectrayan.sse.server.error;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Base runtime exception for SSE server errors with a machine-readable {@link ErrorCode}.
 */
public class SseException extends RuntimeException {

    private final ErrorCode code;
    private final String topic;
    private final Map<String, Object> details;
    private final Instant timestamp;

    public SseException(ErrorCode code, String message) {
        this(code, message, null, null, null);
    }

    public SseException(ErrorCode code, String message, Throwable cause) {
        this(code, message, null, null, cause);
    }

    public SseException(ErrorCode code, String message, String topic) {
        this(code, message, topic, null, null);
    }

    public SseException(ErrorCode code, String message, String topic, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.topic = topic;
        this.details = details == null ? Collections.emptyMap() : Collections.unmodifiableMap(details);
        this.timestamp = Instant.now();
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getTopic() {
        return topic;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
