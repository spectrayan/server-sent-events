package com.spectrayan.sse.server.events;

import java.time.Instant;
import java.util.Objects;

/**
 * Base class for SSE session lifecycle events published by the library.
 *
 * These are NOT Spring's {@code ServerSentEvent} frames. They are application
 * events you can listen to with Spring to react to lifecycle transitions.
 */
public abstract class SseSessionEvent {
    private final String sessionId;
    private final String topic;
    private final String remoteAddress;
    private final Instant timestamp;

    protected SseSessionEvent(String sessionId, String topic, String remoteAddress) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.topic = topic;
        this.remoteAddress = remoteAddress;
        this.timestamp = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public String getTopic() { return topic; }
    public String getRemoteAddress() { return remoteAddress; }
    public Instant getTimestamp() { return timestamp; }
}
