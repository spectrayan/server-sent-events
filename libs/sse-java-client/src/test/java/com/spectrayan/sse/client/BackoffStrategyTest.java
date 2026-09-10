package com.spectrayan.sse.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffStrategyTest {

    @Test
    void shouldComputeInitialDelayWithJitter() {
        SseReconnectionConfig config = SseReconnectionConfig.builder()
            .initialDelay(Duration.ofMillis(1000))
            .maxDelay(Duration.ofSeconds(10))
            .multiplier(2.0)
            .jitter(0.1)
            .build();

        Duration delay = BackoffStrategy.computeDelay(0, config);
        // 1000ms ± 10% = [900, 1100]
        assertThat(delay.toMillis()).isBetween(890L, 1110L);
    }

    @Test
    void shouldExponentiallyScaleDelays() {
        SseReconnectionConfig config = SseReconnectionConfig.builder()
            .initialDelay(Duration.ofMillis(1000))
            .maxDelay(Duration.ofSeconds(60))
            .multiplier(2.0)
            .jitter(0.0)
            .build();

        assertThat(BackoffStrategy.computeDelay(0, config).toMillis()).isEqualTo(1000L);
        assertThat(BackoffStrategy.computeDelay(1, config).toMillis()).isEqualTo(2000L);
        assertThat(BackoffStrategy.computeDelay(2, config).toMillis()).isEqualTo(4000L);
        assertThat(BackoffStrategy.computeDelay(3, config).toMillis()).isEqualTo(8000L);
    }

    @Test
    void shouldCapDelayAtMaxDelay() {
        SseReconnectionConfig config = SseReconnectionConfig.builder()
            .initialDelay(Duration.ofMillis(1000))
            .maxDelay(Duration.ofMillis(5000))
            .multiplier(2.0)
            .jitter(0.0)
            .build();

        // 2^4 * 1000 = 16000 -> capped at 5000
        assertThat(BackoffStrategy.computeDelay(4, config).toMillis()).isEqualTo(5000L);
    }
}