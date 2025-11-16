package com.spectrayan.sse.server.error;

/**
 * Thrown when attempting to broadcast but there are no active topics/subscribers.
 */
public class NoSubscribersException extends SseException {
    /**
     * Create an exception indicating there are no active subscribers/topics.
     *
     * @param message human-readable description
     */
    public NoSubscribersException(String message) {
        super(ErrorCode.NO_SUBSCRIBERS, message);
    }
}
