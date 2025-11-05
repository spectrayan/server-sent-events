package com.spectrayan.sse.server.customize;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

/**
 * Hook to customize the SSE stream per request/topic.
 * Implementations may transform the flux (e.g., map, filter, buffer, add metrics, etc.).
 */
@FunctionalInterface
public interface SseStreamCustomizer {
    Flux<ServerSentEvent<Object>> customize(String topic, ServerWebExchange exchange, Flux<ServerSentEvent<Object>> stream);
}
