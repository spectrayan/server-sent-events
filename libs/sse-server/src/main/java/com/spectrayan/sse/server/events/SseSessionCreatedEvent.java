package com.spectrayan.sse.server.events;

/**
 * Emitted when a new SSE session (HTTP request to the SSE endpoint) is created
 * and before subscription to the topic stream occurs.
 */
public class SseSessionCreatedEvent extends SseSessionEvent {
    public SseSessionCreatedEvent(String sessionId, String topic, String remoteAddress) {
        super(sessionId, topic, remoteAddress);
    }
}
