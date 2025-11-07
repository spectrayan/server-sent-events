package com.spectrayan.sse.server.customize;

import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;

/**
 * Default {@link SessionIdGenerator} that produces random UUID strings.
 */
public class UuidSessionIdGenerator implements SessionIdGenerator {
    @Override
    public String generate(ServerWebExchange exchange, String topic) {
        return UUID.randomUUID().toString();
    }
}
