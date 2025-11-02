package com.spectrayan.sse.server.error;

/**
 * Thrown when attempting to emit to a topic that does not currently exist.
 */
public class TopicNotFoundException extends SseException {
    public TopicNotFoundException(String topic) {
        super(ErrorCode.TOPIC_NOT_FOUND, "Topic not found: " + topic, topic);
    }
}
