package com.spectrayan.sse.client;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Computes randomized exponential backoff intervals matching the Spectrayan SSE protocol.
 */
public final class BackoffStrategy {

    private BackoffStrategy() {}

    public static Duration computeDelay(long attempt, SseReconnectionConfig config) {
        double rawDelayMs = Math.min(
            config.initialDelay().toMillis() * Math.pow(config.multiplier(), attempt),
            (double) config.maxDelay().toMillis()
        );
        double jitterRange = rawDelayMs * config.jitter();
        double minDelay = Math.max(0.0, rawDelayMs - jitterRange);
        double maxDelay = rawDelayMs + jitterRange;
        if (minDelay >= maxDelay) {
            return Duration.ofMillis((long) rawDelayMs);
        }
        double actualDelayMs = ThreadLocalRandom.current().nextDouble(minDelay, maxDelay);
        return Duration.ofMillis((long) actualDelayMs);
    }

    public static Retry createRetry(SseReconnectionConfig config) {
        if (!config.enabled()) {
            return Retry.max(0).filter(t -> false);
        }
        return Retry.from(companion -> companion.flatMap(retrySignal -> {
            long attempt = retrySignal.totalRetries();
            if (config.maxRetries() != null && attempt >= config.maxRetries()) {
                return Mono.error(new SseConnectionException(
                    "Exceeded max reconnection attempts: " + attempt, retrySignal.failure()));
            }
            Duration delay = computeDelay(attempt, config);
            return Mono.delay(delay);
        }));
    }
}