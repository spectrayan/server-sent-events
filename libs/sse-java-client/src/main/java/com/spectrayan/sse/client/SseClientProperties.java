package com.spectrayan.sse.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for Spectrayan SSE Client.
 */
@ConfigurationProperties(prefix = "spectrayan.sse.client")
public class SseClientProperties {

    /**
     * Target base URL of the Server-Sent Events stream provider.
     */
    private String url;

    /**
     * Whether automatic reconnection is enabled.
     */
    private boolean enabled = true;

    /**
     * Initial reconnection delay before first retry.
     */
    private Duration initialDelay = Duration.ofMillis(1000);

    /**
     * Maximum ceiling for exponential backoff retry intervals.
     */
    private Duration maxDelay = Duration.ofSeconds(30);

    /**
     * Multiplier factor applied per reconnection attempt.
     */
    private double multiplier = 1.5;

    /**
     * Random jitter factor (0.0 to 1.0) applied to retry delays.
     */
    private double jitter = 0.2;

    /**
     * Maximum number of reconnection attempts before raising an error (null = infinite).
     */
    private Long maxRetries;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getInitialDelay() { return initialDelay; }
    public void setInitialDelay(Duration initialDelay) { this.initialDelay = initialDelay; }

    public Duration getMaxDelay() { return maxDelay; }
    public void setMaxDelay(Duration maxDelay) { this.maxDelay = maxDelay; }

    public double getMultiplier() { return multiplier; }
    public void setMultiplier(double multiplier) { this.multiplier = multiplier; }

    public double getJitter() { return jitter; }
    public void setJitter(double jitter) { this.jitter = jitter; }

    public Long getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Long maxRetries) { this.maxRetries = maxRetries; }

    public SseReconnectionConfig toReconnectionConfig() {
        return SseReconnectionConfig.builder()
            .enabled(enabled)
            .initialDelay(initialDelay)
            .maxDelay(maxDelay)
            .multiplier(multiplier)
            .jitter(jitter)
            .maxRetries(maxRetries)
            .build();
    }
}