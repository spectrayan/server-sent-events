package com.spectrayan.sse.server.events;

/**
 * Emitted when the stream terminates with an error (disconnect due to error).
 */
public class SseDisconnectedEvent extends SseSessionEvent {
    private final Throwable cause;

    /**
     * Create an event indicating the SSE stream disconnected due to an error.
     *
     * @param sessionId the session identifier
     * @param topic the topic associated with the session
     * @param remoteAddress the client address
     * @param cause the underlying error that caused the disconnection
     */
    public SseDisconnectedEvent(String sessionId, String topic, String remoteAddress, Throwable cause) {
        super(sessionId, topic, remoteAddress);
        this.cause = cause;
    }

    /**
     * The underlying error that triggered the disconnection.
     *
     * @return the cause of disconnection
     */
    public Throwable getCause() {
        return cause;
    }
}
