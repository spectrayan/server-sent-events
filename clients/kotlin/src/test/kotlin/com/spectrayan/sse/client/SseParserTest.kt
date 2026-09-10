package com.spectrayan.sse.client

import com.spectrayan.sse.client.internal.SseParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SseParserTest {

    @Test
    fun testSingleLineEvent() {
        val parser = SseParser()
        val chunk = "id: 101\nevent: greeting\ndata: Hello World\n\n"
        val events = parser.feed(chunk)

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("101", event.id)
        assertEquals("greeting", event.event)
        assertEquals("Hello World", event.data)
        assertEquals("101", parser.lastEventId)
    }

    @Test
    fun testMultilineDataAndComments() {
        val parser = SseParser()
        val chunk = """
            : keepalive ping
            id: 202
            data: line 1
            data: line 2
            data: line 3
            
            
        """.trimIndent()

        val events = parser.feed(chunk)
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("202", event.id)
        assertEquals("message", event.event)
        assertEquals("line 1\nline 2\nline 3", event.data)
        assertEquals(listOf("keepalive ping"), event.comments)
    }

    @Test
    fun testRetryAndLastEventId() {
        val parser = SseParser()
        val chunk = """
            retry: 5000
            id: 42
            data: with retry
            
            
        """.trimIndent()

        val events = parser.feed(chunk)
        assertEquals(1, events.size)
        assertEquals(5000L, events[0].retry)
        assertEquals("42", events[0].id)
        assertEquals("42", parser.lastEventId)
    }

    @Test
    fun testCrlfAndLfLineEndings() {
        val parser = SseParser()
        val chunk = "id: 99\r\nevent: crlf\r\ndata: text with crlf\r\n\r\n"
        val events = parser.feed(chunk)

        assertEquals(1, events.size)
        assertEquals("99", events[0].id)
        assertEquals("crlf", events[0].event)
        assertEquals("text with crlf", events[0].data)
    }

    @Test
    fun testChunkFragmentation() {
        val parser = SseParser()
        // Feed in 3 disjoint fragments slicing across fields and boundaries
        val part1 = "id: 5"
        val part2 = "55\nevent: frag"
        val part3 = "ment\ndata: partial payload\n\n"

        val events1 = parser.feed(part1)
        val events2 = parser.feed(part2)
        val events3 = parser.feed(part3)

        assertEquals(0, events1.size)
        assertEquals(0, events2.size)
        assertEquals(1, events3.size)

        assertEquals("555", events3[0].id)
        assertEquals("fragment", events3[0].event)
        assertEquals("partial payload", events3[0].data)
    }

    @Test
    fun testIgnoreNullInId() {
        val parser = SseParser()
        val chunk = "id: bad\u0000id\ndata: content\n\n"
        val events = parser.feed(chunk)

        assertEquals(1, events.size)
        assertNull(events[0].id)
    }
}
