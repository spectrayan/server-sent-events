package com.spectrayan.sse.server.error;

/**
 * Thrown when a provided topic identifier is null, blank, or does not match the allowed pattern.
 */
public class InvalidTopicException extends SseException {
    /**
     * Create an exception indicating the provided topic identifier is invalid.
     *
     * @param topic the offending topic id
     * @param message details about why the topic is invalid
     */
    public InvalidTopicException(String topic, String message) {
        super(ErrorCode.INVALID_TOPIC, message, topic);
    }
}
