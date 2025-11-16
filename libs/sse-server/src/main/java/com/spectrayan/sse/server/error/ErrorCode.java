package com.spectrayan.sse.server.error;

/**
 * Machine-readable error codes for SSE server operations.
 */
public enum ErrorCode {
    /** The provided topic id is invalid (fails validation rules). */
    INVALID_TOPIC,
    /** The requested topic was not found or has not been created yet. */
    TOPIC_NOT_FOUND,
    /** An operation required subscribers but none were present. */
    NO_SUBSCRIBERS,
    /** The Reactor sink rejected the emission (overflow/terminated/etc.). */
    EMISSION_REJECTED,
    /** The stream terminated unexpectedly. */
    STREAM_TERMINATED,
    /** Payload could not be serialized into an SSE frame. */
    SERIALIZATION_FAILURE,
    /** Heartbeat emission failed. */
    HEARTBEAT_FAILURE,
    /** Subscription was rejected by a filter or policy. */
    SUBSCRIPTION_REJECTED,
    /** Unclassified internal error. */
    INTERNAL_ERROR
}
