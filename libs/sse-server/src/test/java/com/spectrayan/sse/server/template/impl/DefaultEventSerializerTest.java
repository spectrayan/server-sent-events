package com.spectrayan.sse.server.template.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import static org.junit.jupiter.api.Assertions.*;

class DefaultEventSerializerTest {

    @Test
    void wrapsPayloadAndSetsEventAndId() {
        DefaultEventSerializer ser = new DefaultEventSerializer();
        ServerSentEvent<Object> sse = ser.toSse("data", "evt", "id-1");
        assertEquals("evt", sse.event());
        assertEquals("id-1", sse.id());
        assertEquals("data", sse.data());
    }

    @Test
    void allowsNullEventAndId() {
        DefaultEventSerializer ser = new DefaultEventSerializer();
        ServerSentEvent<Object> sse = ser.toSse(42, null, null);
        assertNull(sse.event());
        assertNull(sse.id());
        assertEquals(42, sse.data());
    }
}
