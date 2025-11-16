package com.spectrayan.sse.server.template;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Strategy to map an error on the SSE stream into one or more SSE frames,
 * allowing the stream to continue instead of terminating.
 */
@FunctionalInterface
public interface ErrorMapper {
    /**
     * Convert a stream error into one or more {@link ServerSentEvent} frames so the
     * connection can continue instead of terminating.
     *
     * @param error the error that occurred
     * @param ctx connection context associated with the error
     * @return a flux of SSE frames representing the error
     */
    Flux<ServerSentEvent<Object>> map(Throwable error, SseConnectContext ctx);
}
