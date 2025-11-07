package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.error.InvalidTopicException;

/**
 * Validates topic identifiers against configured rules from {@link com.spectrayan.sse.server.config.SseServerProperties}.
 * <p>
 * Responsibilities:
 * - Reject {@code null} or blank topic ids with {@link com.spectrayan.sse.server.error.InvalidTopicException}.
 * - Enforce an optional regular expression pattern if configured via {@code spectrayan.sse.server.topics.pattern}.
 * <p>
 * This class is package-private and used by the emitter orchestration to keep validation concerns isolated.
 */
final class TopicValidator {

    private final SseServerProperties properties;

    TopicValidator(SseServerProperties properties) {
        this.properties = properties;
    }

    void validateOrThrow(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new InvalidTopicException(topic, "Topic must not be null or blank");
        }
        String pattern = properties.getTopics().getPattern();
        if (pattern != null && !topic.matches(pattern)) {
            throw new InvalidTopicException(topic, "Topic contains illegal characters; allowed pattern: " + pattern);
        }
    }
}
