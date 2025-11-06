package com.spectrayan.sse.server.session;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single SSE subscription session.
 */
public final class SseSession {
    private final String sessionId;
    private final String topic;
    private final String remoteAddress;
    private final String userAgent;
    private final Instant createdAt;
    private final Map<String, Object> attributes;

    private SseSession(Builder b) {
        this.sessionId = b.sessionId != null ? b.sessionId : UUID.randomUUID().toString();
        this.topic = b.topic;
        this.remoteAddress = b.remoteAddress;
        this.userAgent = b.userAgent;
        this.createdAt = b.createdAt != null ? b.createdAt : Instant.now();
        this.attributes = b.attributes != null ? Collections.unmodifiableMap(b.attributes) : Collections.emptyMap();
    }

    public String getSessionId() { return sessionId; }
    public String getTopic() { return topic; }
    public String getRemoteAddress() { return remoteAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public Map<String, Object> getAttributes() { return attributes; }

    public static Builder builder() { return new Builder(); }

    public static SseSession anonymous(String topic) {
        return builder().topic(topic).build();
    }

    public static final class Builder {
        private String sessionId;
        private String topic;
        private String remoteAddress;
        private String userAgent;
        private Instant createdAt;
        private Map<String, Object> attributes;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder topic(String topic) { this.topic = topic; return this; }
        public Builder remoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder attributes(Map<String, Object> attributes) { this.attributes = attributes; return this; }

        public SseSession build() {
            Objects.requireNonNull(topic, "topic");
            return new SseSession(this);
        }
    }
}
