package com.spectrayan.sse.server.template;

import java.util.Map;

/**
 * Immutable context describing a single SSE connection request.
 */
public record SseConnectContext(
        String topic,
        String sessionId,
        String lastEventId,
        String remoteAddress,
        Map<String, String> requestHeaders,
        Map<String, Object> attributes
) {
}
