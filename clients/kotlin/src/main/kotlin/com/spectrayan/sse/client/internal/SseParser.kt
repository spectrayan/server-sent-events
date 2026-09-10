package com.spectrayan.sse.client.internal

import com.spectrayan.sse.client.models.ServerSentEvent

/**
 * Incremental streaming parser conforming to the W3C EventSource (Server-Sent Events) specification.
 *
 * Handles chunk fragmentation, CRLF / LF line endings, multiline data fields, event names,
 * id tracking, retry directives, and comment filtering.
 */
internal class SseParser(initialLastEventId: String? = null) {

    private val lineBuffer = StringBuilder()
    private val dataLines = mutableListOf<String>()
    private val comments = mutableListOf<String>()
    private var currentEvent = ServerSentEvent.DEFAULT_EVENT_TYPE
    private var currentId: String? = null
    private var currentRetry: Long? = null

    /**
     * The last successfully dispatched event ID.
     */
    var lastEventId: String? = initialLastEventId
        private set

    /**
     * Feeds an incoming text [chunk] into the parser, returning any completed [ServerSentEvent] instances.
     */
    fun feed(chunk: String): List<ServerSentEvent> {
        val events = mutableListOf<ServerSentEvent>()
        lineBuffer.append(chunk)

        var searchIndex = 0
        while (searchIndex < lineBuffer.length) {
            val char = lineBuffer[searchIndex]
            if (char == '\n' || char == '\r') {
                val line = lineBuffer.substring(0, searchIndex)
                var skipChars = 1

                // Check for \r\n combination
                if (char == '\r' && searchIndex + 1 < lineBuffer.length && lineBuffer[searchIndex + 1] == '\n') {
                    skipChars = 2
                }

                lineBuffer.delete(0, searchIndex + skipChars)
                searchIndex = 0

                val event = processLine(line)
                if (event != null) {
                    events.add(event)
                }
            } else {
                searchIndex++
            }
        }

        return events
    }

    /**
     * Processes a single SSE text line according to W3C parsing rules.
     */
    fun processLine(line: String): ServerSentEvent? {
        // Empty line indicates event boundary
        if (line.isEmpty()) {
            return dispatchEvent()
        }

        // Comment line
        if (line.startsWith(':')) {
            val commentText = line.removePrefix(":").removePrefix(" ")
            comments.add(commentText)
            return null
        }

        val colonIndex = line.indexOf(':')
        val field: String
        val value: String

        if (colonIndex != -1) {
            field = line.substring(0, colonIndex)
            val rawValue = line.substring(colonIndex + 1)
            // Strip a single leading space if present
            value = if (rawValue.startsWith(' ')) rawValue.substring(1) else rawValue
        } else {
            field = line
            value = ""
        }

        when (field) {
            "data" -> {
                dataLines.add(value)
            }
            "event" -> {
                currentEvent = value
            }
            "id" -> {
                // W3C spec: if value contains null character (U+0000), ignore it
                if (!value.contains('\u0000')) {
                    currentId = value
                }
            }
            "retry" -> {
                val parsed = value.trim().toLongOrNull()
                if (parsed != null && parsed >= 0) {
                    currentRetry = parsed
                }
            }
        }

        return null
    }

    /**
     * Flushes any remaining event if the stream ends cleanly.
     */
    fun finish(): ServerSentEvent? {
        if (lineBuffer.isNotEmpty()) {
            val line = lineBuffer.toString()
            lineBuffer.clear()
            processLine(line)
        }
        return dispatchEvent()
    }

    private fun dispatchEvent(): ServerSentEvent? {
        if (dataLines.isEmpty()) {
            // No data accumulated; if id was set, spec says still update lastEventId
            if (currentId != null) {
                lastEventId = currentId
                currentId = null
            }
            currentEvent = ServerSentEvent.DEFAULT_EVENT_TYPE
            comments.clear()
            currentRetry = null
            return null
        }

        val event = ServerSentEvent(
            id = currentId ?: lastEventId,
            event = currentEvent,
            data = dataLines.joinToString("\n"),
            retry = currentRetry,
            comments = comments.toList()
        )

        if (currentId != null) {
            lastEventId = currentId
        }

        // Reset state for next event
        dataLines.clear()
        currentEvent = ServerSentEvent.DEFAULT_EVENT_TYPE
        currentId = null
        currentRetry = null
        comments.clear()

        return event
    }
}
