package com.spectrayan.sse.server.bridge.nats;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the NATS broadcast bridge.
 * <p>
 * Property prefix: {@code spectrayan.sse.server.bridge.nats}
 *
 * @since 2.1.0
 */
@ConfigurationProperties(prefix = "spectrayan.sse.server.bridge.nats")
public class NatsBridgeProperties {

    /**
     * Whether the NATS broadcast bridge is enabled.
     * Default is {@code true}.
     */
    private boolean enabled = true;

    /**
     * NATS server URL (e.g., {@code nats://localhost:4222}).
     * If an existing {@link io.nats.client.Connection} bean is already registered,
     * this property is ignored in favor of the existing connection.
     */
    private String server = "nats://localhost:4222";

    /**
     * NATS subject name for SSE event fan-out.
     * If not specified, falls back to {@code spectrayan.sse.server.bridge.channel-name}
     * (default: {@code sse-broadcast}).
     */
    private String subject;

    /**
     * Optional NATS queue group name.
     * When {@code null} or empty (default), standard broadcast fan-out is used,
     * delivering events to all connected instances.
     * If specified, subscription is load-balanced across instances in this group.
     */
    private String queueGroup;

    /**
     * Optional authentication token.
     */
    private String token;

    /**
     * Optional authentication username.
     */
    private String username;

    /**
     * Optional authentication password.
     */
    private String password;

    /**
     * Optional path to a user credentials file (.creds) for JWT/NKey authentication.
     */
    private String credentialsFile;

    /**
     * Connection timeout when establishing connection to NATS.
     */
    private Duration connectionTimeout = Duration.ofSeconds(5);

    /**
     * Wait duration between reconnection attempts.
     */
    private Duration reconnectWait = Duration.ofSeconds(2);

    /**
     * Maximum number of reconnect attempts (-1 for unlimited).
     */
    private int maxReconnect = -1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getQueueGroup() {
        return queueGroup;
    }

    public void setQueueGroup(String queueGroup) {
        this.queueGroup = queueGroup;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCredentialsFile() {
        return credentialsFile;
    }

    public void setCredentialsFile(String credentialsFile) {
        this.credentialsFile = credentialsFile;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getReconnectWait() {
        return reconnectWait;
    }

    public void setReconnectWait(Duration reconnectWait) {
        this.reconnectWait = reconnectWait;
    }

    public int getMaxReconnect() {
        return maxReconnect;
    }

    public void setMaxReconnect(int maxReconnect) {
        this.maxReconnect = maxReconnect;
    }
}
