package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.error.ErrorCode;
import com.spectrayan.sse.server.error.SseException;
import com.spectrayan.sse.server.template.SseConnectContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultErrorMapperTest {

    @Test
    void mapsSseExceptionToErrorEvent() {
        DefaultErrorMapper mapper = new DefaultErrorMapper();
        SseConnectContext ctx = new SseConnectContext("orders","s1",null,"r", Map.of(), Map.of());
        SseException ex = new SseException(ErrorCode.SUBSCRIPTION_REJECTED, "denied", "orders");

        Flux<ServerSentEvent<Object>> out = mapper.map(ex, ctx).contextWrite(Context.empty());
        StepVerifier.create(out)
                .assertNext(sse -> assertEquals("error", sse.event()))
                .verifyComplete();
    }

    @Test
    void mapsThrowableToErrorEvent() {
        DefaultErrorMapper mapper = new DefaultErrorMapper();
        SseConnectContext ctx = new SseConnectContext("orders","s1",null,"r", Map.of(), Map.of());
        RuntimeException ex = new RuntimeException("boom");

        Flux<ServerSentEvent<Object>> out = mapper.map(ex, ctx).contextWrite(Context.empty());
        StepVerifier.create(out)
                .assertNext(sse -> assertEquals("error", sse.event()))
                .verifyComplete();
    }
}
