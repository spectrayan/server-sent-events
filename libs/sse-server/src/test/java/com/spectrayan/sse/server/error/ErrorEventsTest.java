package com.spectrayan.sse.server.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorEventsTest {

    @Test
    void fromExceptionBuildsErrorEvent() {
        SseException ex = new SseException(
                ErrorCode.SUBSCRIPTION_REJECTED,
                "not allowed",
                "orders",
                Map.of("k", (Object) "v"),
                null);
        ServerSentEvent<Object> evt = ErrorEvents.fromException(ex, "orders", Context.empty());
        assertEquals("error", evt.event());
        assertNotNull(evt.data());
        assertTrue(evt.data().toString().contains("SUBSCRIPTION_REJECTED"));
    }

    @Test
    void fromThrowableBuildsErrorEvent() {
        RuntimeException t = new RuntimeException("broken");
        ServerSentEvent<Object> evt = ErrorEvents.fromThrowable(t, "foo", Context.empty());
        assertEquals("error", evt.event());
        assertNotNull(evt.data());
        assertTrue(evt.data().toString().contains("INTERNAL_ERROR"));
    }
}
