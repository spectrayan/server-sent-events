package com.spectrayan.sse.server.template;

/**
 * Strategy to decide whether a client connection should be allowed.
 */
@FunctionalInterface
public interface ClientFilter {
    /**
     * Decide whether to allow a new client connection described by the given context.
     *
     * @param ctx immutable description of the incoming connection
     * @return {@code true} to allow the connection; {@code false} to reject it
     */
    boolean allow(SseConnectContext ctx);
}
