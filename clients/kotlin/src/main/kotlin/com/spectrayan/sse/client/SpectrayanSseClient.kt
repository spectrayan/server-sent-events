package com.spectrayan.sse.client

import com.spectrayan.sse.client.config.SseClientConfig
import com.spectrayan.sse.client.internal.BackoffCalculator
import com.spectrayan.sse.client.internal.SseParser
import com.spectrayan.sse.client.models.ServerSentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * High-performance, idiomatic Kotlin client for consuming Server-Sent Events (SSE).
 *
 * Emits events as a cold Coroutines [Flow]. Automatically manages reconnection with
 * randomized exponential jitter and `Last-Event-ID` session resumption.
 *
 * Usage:
 * ```kotlin
 * val client = SpectrayanSseClient("https://example.com/sse/events") {
 *     bearerAuth("secret-token")
 *     reconnection {
 *         initialDelay = 1.seconds
 *         maxDelay = 30.seconds
 *     }
 * }
 *
 * client.stream()
 *     .collect { event ->
 *         println("Received [${event.event}]: ${event.data}")
 *     }
 * ```
 */
class SpectrayanSseClient(
    val url: String,
    val config: SseClientConfig = SseClientConfig()
) : Closeable {

    private val httpClient: OkHttpClient = config.okHttpClient ?: defaultHttpClient()
    private val activeCalls = java.util.concurrent.ConcurrentHashMap.newKeySet<Call>()

    /**
     * Consumes the SSE stream as a cold [Flow] of [ServerSentEvent] items.
     *
     * The stream will automatically reconnect upon transient network or server errors
     * according to the configured [com.spectrayan.sse.client.config.ReconnectionConfig].
     *
     * Cancelling the collecting Coroutine scope cleanly terminates the connection and reclaims resources.
     */
    fun stream(): Flow<ServerSentEvent> = flow {
        var attempt = 0
        var currentLastEventId = config.lastEventId

        while (currentCoroutineContext().isActive) {
            val parser = SseParser(initialLastEventId = currentLastEventId)
            var currentCall: Call? = null

            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")

                // Apply user headers
                for ((key, value) in config.headers) {
                    requestBuilder.header(key, value)
                }

                // Propagate Last-Event-ID if available
                currentLastEventId?.let { id ->
                    if (id.isNotEmpty()) {
                        requestBuilder.header("Last-Event-ID", id)
                    }
                }

                val request = requestBuilder.build()
                val call = httpClient.newCall(request)
                currentCall = call
                activeCalls.add(call)

                val response = call.awaitResponse()

                if (response.code == 204) {
                    // HTTP 204 No Content: W3C specifies the server deliberately closed the stream.
                    response.close()
                    break
                }

                if (!response.isSuccessful) {
                    val code = response.code
                    val msg = response.message
                    response.close()
                    throw IOException("SSE stream request failed with HTTP $code: $msg")
                }

                val body = response.body ?: throw IOException("SSE response body was null")

                // Reset attempt count upon successful connection and response headers
                attempt = 0

                body.use { responseBody ->
                    val source = responseBody.source()
                    while (!source.exhausted() && currentCoroutineContext().isActive) {
                        val line = source.readUtf8Line() ?: break
                        val event = parser.processLine(line)
                        if (event != null) {
                            // Update tracked lastEventId
                            if (event.id != null) {
                                currentLastEventId = event.id
                            }
                            emit(event)
                        }
                    }

                    // Flush any pending trailing event
                    val trailing = parser.finish()
                    if (trailing != null) {
                        if (trailing.id != null) {
                            currentLastEventId = trailing.id
                        }
                        emit(trailing)
                    }
                }

            } catch (e: CancellationException) {
                // Clean coroutine cancellation
                throw e
            } catch (e: Throwable) {
                if (!currentCoroutineContext().isActive) {
                    break
                }

                // Check retry bounds
                val maxRetries = config.reconnection.maxRetries
                if (maxRetries in 1..attempt) {
                    throw IOException("SSE reconnection exceeded maxRetries ($maxRetries)", e)
                }

                // Compute exponential jitter delay
                val backoff = BackoffCalculator.computeDelay(attempt, config.reconnection)
                attempt++

                delay(backoff)
            } finally {
                currentCall?.let {
                    activeCalls.remove(it)
                    it.cancel()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Subscribes exclusively to events matching the given [eventType].
     */
    fun streamEvents(eventType: String): Flow<ServerSentEvent> =
        stream().filter { it.event == eventType }

    /**
     * Closes all active SSE connections and cancels in-flight calls.
     */
    override fun close() {
        for (call in activeCalls) {
            try {
                call.cancel()
            } catch (_: Exception) {
            }
        }
        activeCalls.clear()
    }

    private companion object {
        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // SSE streams require infinite read timeout
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancel()
            }

            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            })
        }
    }
}

/**
 * DSL factory builder for [SpectrayanSseClient].
 */
fun SpectrayanSseClient(
    url: String,
    configure: SseClientConfig.Builder.() -> Unit
): SpectrayanSseClient {
    val builder = SseClientConfig.Builder()
    builder.configure()
    return SpectrayanSseClient(url, builder.build())
}
