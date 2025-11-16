package com.spectrayan.sse.server.session;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single Server-Sent Events (SSE) subscription session.
 * <p>
 * A session is created when a client subscribes to a topic and remains tracked while the
 * subscription is active. It captures useful metadata such as the session id,
 * the topic name, the remote address, the user agent, creation timestamp, and optional
 * arbitrary attributes supplied by the library or application hooks.
 * <p>
 * Session ids are normally provided by a pluggable {@code SessionIdGenerator} from the
 * {@code com.spectrayan.sse.server.customize} package. This class does not generate ids
 * on its own; generation is handled upstream by components such as emitters or endpoint
 * handlers configured with a {@code SessionIdGenerator}.
 */
public final class SseSession {
    /**
     * Unique identifier for this SSE session. Typically produced by a
     * {@code com.spectrayan.sse.server.customize.SessionIdGenerator}.
     */
    private final String sessionId;
    /**
     * The topic name this session is subscribed to.
     */
    private final String topic;
    /**
     * The remote address of the subscribing client, if available (may be {@code null}).
     */
    private final String remoteAddress;
    /**
     * The {@code User-Agent} header from the subscribing client, if available (may be {@code null}).
     */
    private final String userAgent;
    /**
     * Timestamp (UTC) when the session object was created.
     */
    private final Instant createdAt;
    /**
     * Read-only map of optional attributes attached to the session.
     */
    private final Map<String, Object> attributes;

    private SseSession(Builder b) {
        this.sessionId = b.sessionId; // Do not auto-generate here; generation is handled by SessionIdGenerator upstream
        this.topic = b.topic;
        this.remoteAddress = b.remoteAddress;
        this.userAgent = b.userAgent;
        this.createdAt = b.createdAt != null ? b.createdAt : Instant.now();
        this.attributes = b.attributes != null ? Collections.unmodifiableMap(b.attributes) : Collections.emptyMap();
    }

    /**
     * Returns the unique id of this session.
     */
    public String getSessionId() { return sessionId; }
    /**
     * Returns the topic name this session is subscribed to.
     */
    public String getTopic() { return topic; }
    /**
     * Returns the remote address that initiated the subscription, if known.
     */
    public String getRemoteAddress() { return remoteAddress; }
    /**
     * Returns the client {@code User-Agent} header value, if available.
     */
    public String getUserAgent() { return userAgent; }
    /**
     * Returns the timestamp when this session object was created.
     */
    public Instant getCreatedAt() { return createdAt; }
    /**
     * Returns an immutable view of session attributes.
     */
    public Map<String, Object> getAttributes() { return attributes; }

    /**
     * Create a new builder for {@link SseSession}.
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Convenience factory that creates a minimal session for the given topic with
     * default values for other fields. Note: no session id is generated here; callers
     * should assign one using a configured {@code SessionIdGenerator}.
     *
     * @param topic the topic name (must not be {@code null})
     * @return a new {@link SseSession}
     */
    public static SseSession anonymous(String topic) {
        return builder().topic(topic).build();
    }

    /**
     * Builder for immutable {@link SseSession} instances.
     */
    public static final class Builder {
        private String sessionId;
        private String topic;
        private String remoteAddress;
        private String userAgent;
        private Instant createdAt;
        private Map<String, Object> attributes;

        /**
         * Set the session id to use. This class does not auto-generate ids; callers should
         * supply an id produced by a configured {@code SessionIdGenerator}.
         */
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        /**
         * Set the topic name (required).
         */
        public Builder topic(String topic) { this.topic = topic; return this; }
        /**
         * Set the remote address for the subscribing client.
         */
        public Builder remoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; return this; }
        /**
         * Set the client {@code User-Agent} value.
         */
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        /**
         * Set the creation timestamp. If not provided, {@link Instant#now()} is used.
         */
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        /**
         * Set optional attributes for the session. The map will be wrapped in an
         * unmodifiable view during {@link #build()}.
         */
        public Builder attributes(Map<String, Object> attributes) { this.attributes = attributes; return this; }

        /**
         * Construct the {@link SseSession} instance.
         *
         * @throws NullPointerException if topic is {@code null}
         */
        public SseSession build() {
            Objects.requireNonNull(topic, "topic");
            return new SseSession(this);
        }
    }
}
