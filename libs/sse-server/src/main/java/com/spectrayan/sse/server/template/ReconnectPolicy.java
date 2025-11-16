package com.spectrayan.sse.server.template;

import java.util.Optional;

/**
 * Strategy that decides whether to advertise a retry delay via the SSE control line {@code retry: <millis>}.
 */
@FunctionalInterface
public interface ReconnectPolicy {
    /**
     * Determine the reconnection delay to advertise to the client using the SSE control
     * line {@code retry: <millis>}.
     *
     * @param ctx connection context
     * @return an optional delay in milliseconds; empty to omit the retry directive
     */
    Optional<Long> retryDelayMillis(SseConnectContext ctx);
}
