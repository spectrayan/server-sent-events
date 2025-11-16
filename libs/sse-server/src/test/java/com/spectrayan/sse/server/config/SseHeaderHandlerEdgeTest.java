package com.spectrayan.sse.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SseHeaderHandlerEdgeTest {

    @Test
    void getMdcKeysSkipsBlankAndCollectsPresent() {
        SseServerProperties props = new SseServerProperties();

        SseHeader a = new SseHeader();
        a.setKey("X-Tenant");
        a.setMdcKey("tenant");

        SseHeader b = new SseHeader();
        b.setKey("X-Empty");
        b.setMdcKey(""); // blank key should be ignored

        props.setHeaders(List.of(a, b));
        SseHeaderHandler handler = new SseHeaderHandler(props);

        assertEquals(java.util.Set.of("tenant"), handler.getMdcKeys());
    }

    @Test
    void noHeadersConfiguredIsNoOp() {
        SseServerProperties props = new SseServerProperties();
        props.setHeaders(List.of());
        SseHeaderHandler handler = new SseHeaderHandler(props);

        ServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost/sse/t").build());
        // should not throw and should not add any headers
        handler.applyResponseHeaders(ex);
        assertTrue(ex.getResponse().getHeaders().isEmpty());
        // problem headers and mdc map should be empty
        assertTrue(handler.problemHeadersFromMdc().isEmpty());
        assertTrue(handler.getMdcKeys().isEmpty());
    }
}
