package com.spectrayan.sse.server.template;

import java.util.Optional;

/**
 * Strategy that decides whether to advertise a retry delay via the SSE control line {@code retry: <millis>}.
 */
@FunctionalInterface
public interface ReconnectPolicy {
    Optional<Long> retryDelayMillis(SseConnectContext ctx);
}
