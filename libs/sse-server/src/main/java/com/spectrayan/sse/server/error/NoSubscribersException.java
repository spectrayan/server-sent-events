package com.spectrayan.sse.server.error;

/**
 * Thrown when attempting to broadcast but there are no active topics/subscribers.
 */
public class NoSubscribersException extends SseException {
    public NoSubscribersException(String message) {
        super(ErrorCode.NO_SUBSCRIBERS, message);
    }
}
