package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.error.InvalidTopicException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TopicValidatorTest {

    private SseServerProperties props;
    private TopicValidator validator;

    @BeforeEach
    void setUp() {
        props = new SseServerProperties();
        validator = new TopicValidator(props);
    }

    @Test
    void validTopicPassesDefaultPattern() {
        validator.validateOrThrow("orders.v1-NA_123");
        validator.validateOrThrow("A");
        validator.validateOrThrow("abc-DEF_012");
    }

    @Test
    void nullOrBlankRejected() {
        assertThrows(InvalidTopicException.class, () -> validator.validateOrThrow(null));
        assertThrows(InvalidTopicException.class, () -> validator.validateOrThrow(""));
        assertThrows(InvalidTopicException.class, () -> validator.validateOrThrow("   "));
    }

    @Test
    void respectsCustomRegexPattern() {
        props.getTopics().setPattern("^topic-[0-9]{3}$");
        validator = new TopicValidator(props);

        assertDoesNotThrow(() -> validator.validateOrThrow("topic-123"));
        InvalidTopicException ex = assertThrows(InvalidTopicException.class, () -> validator.validateOrThrow("topic-12"));
        assertTrue(ex.getMessage().contains("allowed pattern"));
        assertThrows(InvalidTopicException.class, () -> validator.validateOrThrow("bad"));
    }
}
