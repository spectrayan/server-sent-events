package com.spectrayan.sse.server.template;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Strategy to produce heartbeat events for a connection. Implementations may return
 * an empty flux to disable heartbeats per-connection.
 */
@FunctionalInterface
public interface HeartbeatPolicy {
    /**
     * Produce heartbeat SSE frames for the given connection.
     *
     * @param ctx connection context
     * @return a flux of heartbeat events; may be empty to disable heartbeats
     */
    Flux<ServerSentEvent<Object>> heartbeats(SseConnectContext ctx);
}
