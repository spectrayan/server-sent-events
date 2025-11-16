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

    /**
     * Build a structured SSE error event from a domain {@link SseException}.
     *
     * @param ex the domain exception
     * @param topic the topic associated with the error (overrides ex.topic when non-null)
     * @param contextView Reactor context view (reserved for future enrichment)
     * @return an SSE frame carrying a serialized {@link ErrorPayload}
     */
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

    /**
     * Build a structured SSE error event from an arbitrary {@link Throwable}.
     *
     * @param t the throwable
     * @param topic the topic associated with the error, if known
     * @param contextView Reactor context view (reserved for future enrichment)
     * @return an SSE frame carrying a serialized {@link ErrorPayload}
     */
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
