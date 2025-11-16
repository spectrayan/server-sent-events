package com.spectrayan.sse.server.events;

/**
 * Emitted when the client cancels the subscription (e.g., closes the EventSource or connection drops
 * resulting in cancellation rather than error).
 */
public class SseUnsubscribedEvent extends SseSessionEvent {
    public SseUnsubscribedEvent(String sessionId, String topic, String remoteAddress) {
        super(sessionId, topic, remoteAddress);
    }
}
