package com.spectrayan.sse.client.internal

import com.spectrayan.sse.client.config.ReconnectionConfig
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Calculates exponential backoff durations with full randomized jitter.
 */
internal object BackoffCalculator {

    /**
     * Computes the backoff duration for the given [attempt] index (0-indexed).
     *
     * @param attempt The 0-based retry attempt number (0 for the 1st reconnect).
     * @param config The reconnection configuration.
     * @param randomFactor A random value between 0.0 and 1.0 (defaults to [Random.nextDouble]).
     * @return The calculated [Duration] delay.
     */
    fun computeDelay(
        attempt: Int,
        config: ReconnectionConfig,
        randomFactor: Double = Random.nextDouble()
    ): Duration {
        val initialMs = config.initialDelay.inWholeMilliseconds.toDouble()
        val maxMs = config.maxDelay.inWholeMilliseconds.toDouble()

        // exponential = initial * multiplier^attempt
        val exponentialMs = initialMs * config.multiplier.pow(attempt.toDouble())
        val cappedMs = min(exponentialMs, maxMs)

        // jitter: apply ±jitter range [1.0 - jitter, 1.0 + jitter]
        val minJitter = 1.0 - config.jitter
        val maxJitter = 1.0 + config.jitter
        val jitterMultiplier = minJitter + (randomFactor * (maxJitter - minJitter))

        val finalMs = (cappedMs * jitterMultiplier).toLong().coerceAtLeast(0L)
        return finalMs.milliseconds
    }
}
