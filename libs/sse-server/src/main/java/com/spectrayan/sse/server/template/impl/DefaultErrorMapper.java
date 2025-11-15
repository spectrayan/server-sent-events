package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.error.ErrorEvents;
import com.spectrayan.sse.server.error.SseException;
import com.spectrayan.sse.server.template.ErrorMapper;
import com.spectrayan.sse.server.template.SseConnectContext;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default mapper that converts errors into a single structured SSE event using ErrorEvents helpers.
 */
public class DefaultErrorMapper implements ErrorMapper {
    @Override
    public Flux<ServerSentEvent<Object>> map(Throwable error, SseConnectContext ctx) {
        return Mono.deferContextual(view -> {
            if (error instanceof SseException se) {
                return Mono.just(ErrorEvents.fromException(se, ctx.topic(), view));
            }
            return Mono.just(ErrorEvents.fromThrowable(error, ctx.topic(), view));
        }).flux();
    }
}
