package com.spectrayan.sse.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class SseServerWebFilterTest {

    @Test
    void seedsMdcAndPropagatesToReactorContext() {
        // Configure properties with one header rule mapping X-Tenant -> MDC key 'tenant'
        SseServerProperties props = new SseServerProperties();
        props.setMdcBridgeEnabled(true);

        SseHeader h = new SseHeader();
        h.setKey("X-Tenant");
        h.setMdcKey("tenant");
        props.setHeaders(java.util.List.of(h));

        SseHeaderHandler headerHandler = new SseHeaderHandler(props);
        SseServerWebFilter filter = new SseServerWebFilter(props, headerHandler);

        MockServerHttpRequest req = MockServerHttpRequest.get("http://localhost/api/test")
                .header("X-Tenant", "acme")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(req);

        // Chain that asserts MDC is set during filter and Reactor Context contains keys
        WebFilterChain chain = new WebFilterChain() {
            @Override
            public Mono<Void> filter(ServerWebExchange e) {
                // Inside the chain, MDC should have the tenant value
                assertEquals("acme", org.slf4j.MDC.get("tenant"));
                return Mono.deferContextual(ctx -> {
                    assertTrue(ctx.hasKey(props.getMdcContextKey()));
                    assertEquals(Boolean.TRUE, ctx.getOrDefault(props.getMdcContextKey(), Boolean.FALSE));
                    assertEquals("acme", ctx.get("tenant"));
                    return Mono.empty();
                });
            }
        };

        // Execute filter chain
        filter.filter(exchange, chain).block();

        // After completion, MDC must be cleared by the filter
        assertNull(org.slf4j.MDC.get("tenant"));
    }
}
