package com.spectrayan.sse.server.customize;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Hook to customize headers before the SSE response is committed.
 */
@FunctionalInterface
public interface SseHeaderCustomizer {
    /**
     * Customize the response headers for an SSE response before it is committed.
     *
     * @param exchange current server exchange
     * @param responseHeaders mutable response headers to customize
     */
    void customize(ServerWebExchange exchange, HttpHeaders responseHeaders);
}
