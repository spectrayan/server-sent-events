package com.spectrayan.sse.server.customize;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Hook to customize headers before the SSE response is committed.
 */
@FunctionalInterface
public interface SseHeaderCustomizer {
    void customize(ServerWebExchange exchange, HttpHeaders responseHeaders);
}
