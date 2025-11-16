package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.customize.SseEmitterCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSseEmitterIntegrationTest {

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return null; }
            @Override public T getIfAvailable() { return null; }
            @Override public T getIfUnique() { return null; }
            @Override public java.util.stream.Stream<T> orderedStream() { return java.util.stream.Stream.empty(); }
            @Override public java.util.Iterator<T> iterator() { return java.util.List.<T>of().iterator(); }
        };
    }

    private DefaultSseEmitter newEmitter() {
        SseServerProperties props = new SseServerProperties();
        return new DefaultSseEmitter(props, emptyProvider(), emptyProvider(), (exchange, topic) -> "sid");
    }

    @Test
    void connectWithInvalidTopicThrows() {
        DefaultSseEmitter emitter = newEmitter();
        String invalid = "bad topic with space"; // violates default pattern
        assertThrows(com.spectrayan.sse.server.error.InvalidTopicException.class, () -> emitter.connect(invalid));
    }

    @Test
    void broadcastWithNoTopicsIsNoOp() {
        DefaultSseEmitter emitter = newEmitter();
        // no topics created
        assertDoesNotThrow(() -> emitter.emitToAll("hello"));
    }

    @Test
    void currentTopicsReflectsCreatedTopic() {
        DefaultSseEmitter emitter = newEmitter();
        // getOrCreate is invoked when connect(String) is called (before subscription)
        emitter.connect("demo");
        assertTrue(emitter.currentTopics().contains("demo"));
    }
}
