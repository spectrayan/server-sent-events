package com.spectrayan.sse.server.events;

/**
 * Emitted when the stream completes normally (no error) on the server side.
 */
public class SseSessionClosedEvent extends SseSessionEvent {
    public SseSessionClosedEvent(String sessionId, String topic, String remoteAddress) {
        super(sessionId, topic, remoteAddress);
    }
}
