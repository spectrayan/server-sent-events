package com.spectrayan.sse.server.customize;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.function.Supplier;

/**
 * Endpoint-level customization hook that can wrap the entire SSE handling logic.
 *
 * Implementations can short-circuit, decorate, or replace the default behavior.
 * Use this for cross-cutting concerns that need visibility before/after stream customization
 * (e.g., path-based gating, auth checks, metering, multi-tenant scoping, etc.).
 */
@FunctionalInterface
public interface SseEndpointCustomizer {
    /**
     * @param topic the requested topic path variable
     * @param exchange the current WebFlux exchange
     * @param next supplies the default endpoint behavior when invoked
     * @return a (possibly transformed) SSE flux
     */
    Flux<ServerSentEvent<Object>> handle(String topic, ServerWebExchange exchange, Supplier<Flux<ServerSentEvent<Object>>> next);
}
