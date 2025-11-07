package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.customize.SseSessionHook;
import com.spectrayan.sse.server.session.SseSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionTrackerTest {

    private static class RecordingHook implements SseSessionHook {
        final List<String> joined = new ArrayList<>();
        final List<reactor.core.publisher.SignalType> left = new ArrayList<>();
        @Override public void onJoin(SseSession session) { joined.add(session.getSessionId()); }
        @Override public void onLeave(SseSession session, reactor.core.publisher.SignalType sig) { left.add(sig); }
    }

    private TopicManager topicManager;
    private RecordingHook hook;
    private SessionTracker tracker;

    @BeforeEach
    void setUp() {
        // TopicManager needs a SinkFactory; properties don't matter here for sink creation default
        com.spectrayan.sse.server.config.SseServerProperties props = new com.spectrayan.sse.server.config.SseServerProperties();
        SinkFactory sinkFactory = new SinkFactory(props, null);
        topicManager = new TopicManager(sinkFactory);
        hook = new RecordingHook();
        tracker = new SessionTracker(List.of(hook), topicManager);
    }

    @Test
    void onSubscribeRegistersSessionAndIncrementsCount_thenCancelCleansUpWhenLast() {
        String topic = "demo";
        TopicChannel ch = topicManager.getOrCreate(topic);
        // Upstream: simple sink-based flux we can cancel
        Flux<ServerSentEvent<Object>> upstream = ch.sink.asFlux();
        SseSession session = SseSession.builder().sessionId("s1").topic(topic).build();

        Flux<ServerSentEvent<Object>> decorated = tracker.decorate(topic, upstream, ch, session);

        StepVerifier.create(decorated)
                .then(() -> {
                    // After subscription
                    assertEquals(1, ch.subscribers.get());
                    assertTrue(ch.sessions.containsKey("s1"));
                    assertEquals(List.of("s1"), hook.joined);
                })
                .thenCancel()
                .verify();

        // After cancel, since last subscriber, topic should be removed
        assertNull(topicManager.get(topic), "Topic must be removed on CANCEL when last subscriber");
        assertEquals(List.of(reactor.core.publisher.SignalType.CANCEL), hook.left);
    }

    @Test
    void onErrorCleansUpWhenLast() {
        String topic = "err";
        TopicChannel ch = topicManager.getOrCreate(topic);
        SseSession session = SseSession.builder().sessionId("s2").topic(topic).build();

        // Subscribe to the live sink-backed stream and then actively emit an error.
        // Using concatWith(Flux.error) would never trigger because sink.asFlux() does not complete on its own.
        Sinks.Many<ServerSentEvent<Object>> sink = ch.sink;
        Flux<ServerSentEvent<Object>> upstream = sink.asFlux();

        Flux<ServerSentEvent<Object>> decorated = tracker.decorate(topic, upstream, ch, session);

        StepVerifier.create(decorated)
                .then(() -> sink.tryEmitError(new RuntimeException("boom")))
                .expectError()
                .verify();

        // Should remove topic due to ON_ERROR when last
        assertNull(topicManager.get(topic));
        assertTrue(hook.left.contains(reactor.core.publisher.SignalType.ON_ERROR));
    }

    @Test
    void onCompleteDoesNotCleanupTopic() {
        String topic = "complete";
        TopicChannel ch = topicManager.getOrCreate(topic);
        SseSession session = SseSession.builder().sessionId("s3").topic(topic).build();

        Flux<ServerSentEvent<Object>> upstream = Flux.<ServerSentEvent<Object>>empty();
        Flux<ServerSentEvent<Object>> decorated = tracker.decorate(topic, upstream, ch, session);

        StepVerifier.create(decorated).verifyComplete();

        // ON_COMPLETE does not trigger cleanup according to implementation
        assertNotNull(topicManager.get(topic));
        assertEquals(0, ch.subscribers.get());
        assertTrue(hook.left.contains(reactor.core.publisher.SignalType.ON_COMPLETE));
    }
}
