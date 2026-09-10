package com.spectrayan.sse.client

import com.spectrayan.sse.client.config.ReconnectionConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SpectrayanSseClientTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testStreamEventsSuccessful() = runTest {
        val body = """
            id: 1
            event: greeting
            data: hello
            
            id: 2
            event: greeting
            data: world
            
            
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
        )

        val client = SpectrayanSseClient(server.url("/events").toString())
        val events = client.stream().take(2).toList()

        assertEquals(2, events.size)
        assertEquals("1", events[0].id)
        assertEquals("greeting", events[0].event)
        assertEquals("hello", events[0].data)

        assertEquals("2", events[1].id)
        assertEquals("greeting", events[1].event)
        assertEquals("world", events[1].data)

        val recordedRequest = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("text/event-stream", recordedRequest?.getHeader("Accept"))
    }

    @Test
    fun testStreamEventsFiltering() = runTest {
        val body = """
            event: user_join
            data: Alice
            
            event: message
            data: Hello Alice!
            
            event: user_join
            data: Bob
            
            
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
        )

        val client = SpectrayanSseClient(server.url("/events").toString())
        val userJoins = client.streamEvents("user_join").take(2).toList()

        assertEquals(2, userJoins.size)
        assertEquals("Alice", userJoins[0].data)
        assertEquals("Bob", userJoins[1].data)
    }

    @Test
    fun testHttp204StreamTermination() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        val client = SpectrayanSseClient(server.url("/events").toString())
        val events = client.stream().toList()

        assertEquals(0, events.size)
    }

    @Test
    fun testAutomaticReconnectionAndLastEventId() = runTest {
        // First connection: emit event id: 100 then disconnect abruptly
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("id: 100\ndata: first\n\n")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
        )

        // Second connection: emit event id: 101
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("id: 101\ndata: second\n\n")
        )

        val client = SpectrayanSseClient(server.url("/events").toString()) {
            reconnection {
                initialDelay = 10.milliseconds
                maxDelay = 50.milliseconds
                jitter = 0.0
            }
        }

        val events = client.stream().take(2).toList()

        assertEquals(2, events.size)
        assertEquals("100", events[0].id)
        assertEquals("first", events[0].data)
        assertEquals("101", events[1].id)
        assertEquals("second", events[1].data)

        // Verify requests sent to server
        val request1 = server.takeRequest(5, TimeUnit.SECONDS)
        val request2 = server.takeRequest(5, TimeUnit.SECONDS)

        assertEquals(null, request1?.getHeader("Last-Event-ID"))
        assertEquals("100", request2?.getHeader("Last-Event-ID"))
    }

    @Test
    fun testContextCancellationStopsStream() = runTest {
        // Enqueue an indefinite response
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("id: 1\ndata: endless\n\n")
        )

        val client = SpectrayanSseClient(server.url("/events").toString())

        val job = launch {
            client.stream().collect {
                // Keep collecting
            }
        }

        delay(100)
        job.cancel()
        assertTrue(job.isCancelled)
    }
}
