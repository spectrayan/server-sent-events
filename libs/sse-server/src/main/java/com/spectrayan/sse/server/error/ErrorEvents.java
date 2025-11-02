package com.spectrayan.sse.server.error;

import org.springframework.http.codec.ServerSentEvent;
import reactor.util.context.ContextView;

import java.time.Instant;
import java.util.Map;

/**
 * Helpers to build structured SSE error events.
 */
public final class ErrorEvents {

    private ErrorEvents() {}

    public static ServerSentEvent<Object> fromException(SseException ex, String topic, ContextView contextView) {
        ErrorPayload payload = new ErrorPayload(
                ex.getCode().name(),
                ex.getMessage(),
                topic != null ? topic : ex.getTopic(),
                ex.getTimestamp(),
                ex.getDetails()
        );
        return ServerSentEvent.builder((Object) payload)
                .event("error")
                .build();
    }

    public static ServerSentEvent<Object> fromThrowable(Throwable t, String topic, ContextView contextView) {
        ErrorPayload payload = new ErrorPayload(
                ErrorCode.INTERNAL_ERROR.name(),
                t.getMessage() != null ? t.getMessage() : t.toString(),
                topic,
                Instant.now(),
                Map.of("exception", t.getClass().getSimpleName())
        );
        return ServerSentEvent.builder((Object) payload)
                .event("error")
                .build();
    }
}
