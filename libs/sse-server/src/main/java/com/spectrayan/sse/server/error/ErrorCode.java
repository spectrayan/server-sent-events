package com.spectrayan.sse.server.error;

/**
 * Machine-readable error codes for SSE server operations.
 */
public enum ErrorCode {
    INVALID_TOPIC,
    TOPIC_NOT_FOUND,
    NO_SUBSCRIBERS,
    EMISSION_REJECTED,
    STREAM_TERMINATED,
    SERIALIZATION_FAILURE,
    HEARTBEAT_FAILURE,
    SUBSCRIPTION_REJECTED,
    INTERNAL_ERROR
}
