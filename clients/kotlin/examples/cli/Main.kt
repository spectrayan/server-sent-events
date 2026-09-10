package com.spectrayan.sse.client.examples.cli

import com.spectrayan.sse.client.SpectrayanSseClient
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) = runBlocking {
    val url = args.firstOrNull() ?: "http://localhost:8080/sse/events"
    println("Connecting to SSE stream at $url ...")

    val client = SpectrayanSseClient(url) {
        reconnection {
            initialDelay = 1.seconds
            maxDelay = 15.seconds
            jitter = 0.2
        }
    }

    client.stream()
        .collect { event ->
            println("Event [${event.event}] ID: ${event.id.orEmpty()}")
            println(event.data)
            println("-".repeat(40))
        }
}
