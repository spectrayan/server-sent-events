package com.spectrayan.sse.client.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for exponential backoff reconnection with randomized full jitter.
 *
 * Backoff formula:
 * ```
 * delay = min(initialDelay * multiplier^attempt, maxDelay) * (1 ± jitter)
 * ```
 *
 * @property initialDelay The base delay for the first reconnection attempt.
 * @property maxDelay The upper bound cap for backoff delays.
 * @property multiplier The exponential growth factor per failed attempt.
 * @property jitter The randomized jitter fraction (between 0.0 and 1.0, e.g. 0.2 for ±20%).
 * @property maxRetries Maximum number of reconnection attempts before giving up (0 = unlimited).
 */
data class ReconnectionConfig(
    val initialDelay: Duration = 1.seconds,
    val maxDelay: Duration = 30.seconds,
    val multiplier: Double = 2.0,
    val jitter: Double = 0.2,
    val maxRetries: Int = 0
) {
    init {
        require(initialDelay > Duration.ZERO) { "initialDelay must be greater than zero, got $initialDelay" }
        require(maxDelay >= initialDelay) { "maxDelay ($maxDelay) must be greater than or equal to initialDelay ($initialDelay)" }
        require(multiplier >= 1.0) { "multiplier must be at least 1.0, got $multiplier" }
        require(jitter in 0.0..1.0) { "jitter must be between 0.0 and 1.0, got $jitter" }
        require(maxRetries >= 0) { "maxRetries cannot be negative, got $maxRetries" }
    }

    companion object {
        val DEFAULT = ReconnectionConfig()

        fun disabled(): ReconnectionConfig = ReconnectionConfig(
            initialDelay = 1.seconds,
            maxDelay = 1.seconds,
            maxRetries = 0
        )
    }
}
