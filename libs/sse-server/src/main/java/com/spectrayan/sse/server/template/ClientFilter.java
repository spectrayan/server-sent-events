package com.spectrayan.sse.server.template;

/**
 * Strategy to decide whether a client connection should be allowed.
 */
@FunctionalInterface
public interface ClientFilter {
    boolean allow(SseConnectContext ctx);
}
