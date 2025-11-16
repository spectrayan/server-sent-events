package com.spectrayan.sse.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SseHeaderHandlerTest {

    private ServerWebExchange exchangeWithHeaders(Map<String, String> in) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("http://localhost/sse/topic");
        in.forEach(builder::header);
        MockServerHttpRequest req = builder.build();
        return MockServerWebExchange.from(req);
    }

    @Test
    void applyResponseHeaders_staticAndCopy_skipAccessControl_singleValued() {
        SseServerProperties props = new SseServerProperties();

        // Static header
        SseHeader h1 = new SseHeader();
        h1.setKey("X-App");
        h1.setValue("sse");

        // Copy header
        SseHeader h2 = new SseHeader();
        h2.setKey("X-Trace");
        h2.setCopyToResponse(true);

        // Access-Control-* should be ignored here
        SseHeader h3 = new SseHeader();
        h3.setKey("Access-Control-Allow-Origin");
        h3.setValue("*");

        props.setHeaders(List.of(h1, h2, h3));
        SseHeaderHandler handler = new SseHeaderHandler(props);

        ServerWebExchange ex = exchangeWithHeaders(Map.of("X-Trace", "abc", "Access-Control-Allow-Origin", "*"));
        // Pre-set a conflicting value to check single-valued replacement behavior
        ex.getResponse().getHeaders().add("X-App", "old");

        handler.applyResponseHeaders(ex);

        HttpHeaders out = ex.getResponse().getHeaders();
        assertEquals("sse", out.getFirst("X-App"), "static value should replace existing");
        assertEquals("abc", out.getFirst("X-Trace"), "copied from request");
        assertNull(out.getFirst("Access-Control-Allow-Origin"), "Access-Control-* must be ignored by handler");

        // Duplicate value should not add another header value
        handler.applyResponseHeaders(ex);
        assertEquals(1, out.get("X-App").size());
    }

    @Test
    void seedMdcAndProblemHeaders() {
        SseServerProperties props = new SseServerProperties();

        SseHeader h = new SseHeader();
        h.setKey("X-Request-Id");
        h.setMdcKey("reqId");
        h.setIncludeInProblem(true);
        props.setHeaders(List.of(h));

        SseHeaderHandler handler = new SseHeaderHandler(props);
        ServerWebExchange ex = exchangeWithHeaders(Map.of("X-Request-Id", "r-123"));

        var m = handler.seedMdcFromRequest(ex);
        assertEquals(Map.of("reqId", "r-123"), m);

        var prob = handler.problemHeadersFromMdc();
        assertEquals(Map.of("X-Request-Id", "r-123"), prob);
    }
}
