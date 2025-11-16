package com.spectrayan.sse.server.customize;

import org.springframework.web.server.ServerWebExchange;

/**
 * Strategy SPI for generating SSE session ids.
 * <p>
 * The library provides a default UUID-based implementation, but applications can
 * define their own {@code @Bean SessionIdGenerator} to override it.
 */
public interface SessionIdGenerator {
    /**
     * Generate a new session id.
     *
     * @param exchange the current exchange (may be null)
     * @param topic the topic being subscribed to (may be null)
     * @return a non-null, non-empty session id string
     */
    String generate(ServerWebExchange exchange, String topic);
}
