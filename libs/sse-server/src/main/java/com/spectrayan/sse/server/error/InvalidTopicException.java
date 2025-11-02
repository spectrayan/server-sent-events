package com.spectrayan.sse.server.error;

/**
 * Thrown when a provided topic identifier is null, blank, or does not match the allowed pattern.
 */
public class InvalidTopicException extends SseException {
    public InvalidTopicException(String topic, String message) {
        super(ErrorCode.INVALID_TOPIC, message, topic);
    }
}
