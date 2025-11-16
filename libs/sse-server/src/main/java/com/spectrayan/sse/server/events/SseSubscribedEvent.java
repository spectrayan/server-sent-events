package com.spectrayan.sse.server.events;

/**
 * Emitted when the client subscribes to the topic stream (Flux subscription begins).
 */
public class SseSubscribedEvent extends SseSessionEvent {
    public SseSubscribedEvent(String sessionId, String topic, String remoteAddress) {
        super(sessionId, topic, remoteAddress);
    }
}
