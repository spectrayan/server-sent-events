package com.spectrayan.sse.client

import com.spectrayan.sse.client.config.ReconnectionConfig
import com.spectrayan.sse.client.internal.BackoffCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackoffCalculatorTest {

    @Test
    fun testDefaults() {
        val config = ReconnectionConfig()
        assertEquals(1.seconds, config.initialDelay)
        assertEquals(30.seconds, config.maxDelay)
        assertEquals(2.0, config.multiplier)
        assertEquals(0.2, config.jitter)
        assertEquals(0, config.maxRetries)
    }

    @Test
    fun testExponentialGrowthWithoutJitter() {
        val config = ReconnectionConfig(
            initialDelay = 1.seconds,
            maxDelay = 60.seconds,
            multiplier = 2.0,
            jitter = 0.0
        )

        val delay0 = BackoffCalculator.computeDelay(0, config, randomFactor = 0.5)
        val delay1 = BackoffCalculator.computeDelay(1, config, randomFactor = 0.5)
        val delay2 = BackoffCalculator.computeDelay(2, config, randomFactor = 0.5)
        val delay3 = BackoffCalculator.computeDelay(3, config, randomFactor = 0.5)

        assertEquals(1000.milliseconds, delay0)
        assertEquals(2000.milliseconds, delay1)
        assertEquals(4000.milliseconds, delay2)
        assertEquals(8000.milliseconds, delay3)
    }

    @Test
    fun testMaxDelayCap() {
        val config = ReconnectionConfig(
            initialDelay = 5.seconds,
            maxDelay = 10.seconds,
            multiplier = 3.0,
            jitter = 0.0
        )

        val delay3 = BackoffCalculator.computeDelay(3, config, randomFactor = 0.5)
        assertEquals(10.seconds, delay3)
    }

    @Test
    fun testJitterBounds() {
        val config = ReconnectionConfig(
            initialDelay = 10.seconds,
            maxDelay = 60.seconds,
            multiplier = 2.0,
            jitter = 0.2 // ±20% -> range [8s, 12s] for attempt 0
        )

        val minDelay = BackoffCalculator.computeDelay(0, config, randomFactor = 0.0)
        val maxDelay = BackoffCalculator.computeDelay(0, config, randomFactor = 1.0)
        val midDelay = BackoffCalculator.computeDelay(0, config, randomFactor = 0.5)

        assertEquals(8000.milliseconds, minDelay)
        assertEquals(12000.milliseconds, maxDelay)
        assertEquals(10000.milliseconds, midDelay)

        assertTrue(minDelay in 8.seconds..12.seconds)
        assertTrue(maxDelay in 8.seconds..12.seconds)
    }
}
