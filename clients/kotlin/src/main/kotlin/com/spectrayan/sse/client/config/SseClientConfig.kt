package com.spectrayan.sse.client.config

import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration options for the [com.spectrayan.sse.client.SpectrayanSseClient].
 */
data class SseClientConfig(
    val headers: Map<String, String> = emptyMap(),
    val lastEventId: String? = null,
    val reconnection: ReconnectionConfig = ReconnectionConfig.DEFAULT,
    val okHttpClient: OkHttpClient? = null,
    val bufferCapacity: Int = 128
) {
    class Builder {
        private val headers = mutableMapOf<String, String>()
        private var lastEventId: String? = null
        private var reconnection: ReconnectionConfig = ReconnectionConfig.DEFAULT
        private var okHttpClient: OkHttpClient? = null
        private var bufferCapacity: Int = 128

        fun header(name: String, value: String) = apply {
            headers[name] = value
        }

        fun headers(map: Map<String, String>) = apply {
            headers.putAll(map)
        }

        fun bearerAuth(token: String) = apply {
            header("Authorization", "Bearer $token")
        }

        fun lastEventId(id: String?) = apply {
            this.lastEventId = id
        }

        fun reconnection(config: ReconnectionConfig) = apply {
            this.reconnection = config
        }

        fun reconnection(block: ReconnectionBuilder.() -> Unit) = apply {
            val builder = ReconnectionBuilder()
            builder.block()
            this.reconnection = builder.build()
        }

        fun client(client: OkHttpClient) = apply {
            this.okHttpClient = client
        }

        fun bufferCapacity(capacity: Int) = apply {
            require(capacity > 0) { "bufferCapacity must be > 0" }
            this.bufferCapacity = capacity
        }

        fun build(): SseClientConfig = SseClientConfig(
            headers = headers.toMap(),
            lastEventId = lastEventId,
            reconnection = reconnection,
            okHttpClient = okHttpClient,
            bufferCapacity = bufferCapacity
        )
    }

    class ReconnectionBuilder {
        var initialDelay: Duration = 1.seconds
        var maxDelay: Duration = 30.seconds
        var multiplier: Double = 2.0
        var jitter: Double = 0.2
        var maxRetries: Int = 0

        fun build(): ReconnectionConfig = ReconnectionConfig(
            initialDelay = initialDelay,
            maxDelay = maxDelay,
            multiplier = multiplier,
            jitter = jitter,
            maxRetries = maxRetries
        )
    }
}
