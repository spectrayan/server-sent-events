package com.spectrayan.sse.client;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for exponential backoff, jitter, and automatic reconnection behavior.
 */
public record SseReconnectionConfig(
    boolean enabled,
    Duration initialDelay,
    Duration maxDelay,
    double multiplier,
    double jitter,
    Long maxRetries
) {
    public SseReconnectionConfig {
        Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        Objects.requireNonNull(maxDelay, "maxDelay must not be null");
        if (multiplier <= 1.0) {
            throw new IllegalArgumentException("multiplier must be greater than 1.0");
        }
        if (jitter < 0.0 || jitter > 1.0) {
            throw new IllegalArgumentException("jitter must be between 0.0 and 1.0");
        }
    }

    public static SseReconnectionConfig defaultConfiguration() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enabled = true;
        private Duration initialDelay = Duration.ofMillis(1000);
        private Duration maxDelay = Duration.ofSeconds(30);
        private double multiplier = 1.5;
        private double jitter = 0.2;
        private Long maxRetries = null;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder jitter(double jitter) {
            this.jitter = jitter;
            return this;
        }

        public Builder maxRetries(Long maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public SseReconnectionConfig build() {
            return new SseReconnectionConfig(enabled, initialDelay, maxDelay, multiplier, jitter, maxRetries);
        }
    }
}