package com.spectrayan.sse.server.template;

import java.util.Map;

/**
 * Immutable context describing a single SSE connection request.
 *
 * @param topic the requested topic identifier
 * @param sessionId unique id assigned to this session (may be generated)
 * @param lastEventId value of the {@code Last-Event-ID} header if provided by the client
 * @param remoteAddress textual representation of the client address
 * @param requestHeaders read-only snapshot of inbound HTTP headers
 * @param attributes arbitrary attributes associated with the request/session
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
