package com.spectrayan.sse.client;

/**
 * Exception thrown when an SSE connection fails and reconnection attempts are exhausted or disabled.
 */
public class SseConnectionException extends RuntimeException {

    public SseConnectionException(String message) {
        super(message);
    }

    public SseConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}