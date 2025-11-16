package com.spectrayan.sse.server.template;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Strategy to map an error on the SSE stream into one or more SSE frames,
 * allowing the stream to continue instead of terminating.
 */
@FunctionalInterface
public interface ErrorMapper {
    Flux<ServerSentEvent<Object>> map(Throwable error, SseConnectContext ctx);
}
