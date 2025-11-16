package com.spectrayan.sse.server.events;

/**
 * Emitted when the stream terminates with an error (disconnect due to error).
 */
public class SseDisconnectedEvent extends SseSessionEvent {
    private final Throwable cause;

    public SseDisconnectedEvent(String sessionId, String topic, String remoteAddress, Throwable cause) {
        super(sessionId, topic, remoteAddress);
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }
}
