package com.spectrayan.sse.client;

import java.time.Duration;

/**
 * Represents a strongly-typed Server-Sent Event conforming to the W3C specification.
 *
 * @param <T>       the deserialized payload data type
 * @param id        the unique event identifier, if specified
 * @param event     the event name / type (defaults to "message")
 * @param data      the typed data payload
 * @param retry     the reconnection retry delay advised by the server
 * @param rawData   the raw unparsed string data
 */
public record SseEvent<T>(
    String id,
    String event,
    T data,
    Duration retry,
    String rawData
) {
    public static <T> SseEvent<T> of(String id, String event, T data, Duration retry, String rawData) {
        return new SseEvent<>(id, event != null && !event.isBlank() ? event : "message", data, retry, rawData);
    }
}