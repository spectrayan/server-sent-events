package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.error.EmissionRejectedException;
import com.spectrayan.sse.server.error.TopicNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EmissionServiceTest {

    private SseServerProperties props;
    private SinkFactory sinkFactory;
    private TopicManager topicManager;
    private EmissionService emissionService;

    @BeforeEach
    void setUp() {
        props = new SseServerProperties();
        sinkFactory = new SinkFactory(props, null);
        topicManager = new TopicManager(sinkFactory);
        emissionService = new EmissionService(null, SseServerProperties.Emitter.DEFAULT_EMIT_RETRIES);
    }

    @Test
    void emitToExistingTopicDeliversEvent() {
        String topic = "t1";
        TopicChannel ch = topicManager.getOrCreate(topic);
        Flux<ServerSentEvent<Object>> flux = ch.sink.asFlux();
        StepVerifier verifier = StepVerifier.create(flux).then(() -> {
            emissionService.emitToTopic(topicManager, topic, "evt", List.of(1,2,3), "id-1");
        }).assertNext(sse -> {
            assertEquals("evt", sse.event());
            assertEquals("id-1", sse.id());
            assertEquals(List.of(1,2,3), sse.data());
        }).thenCancel().verifyLater();
        verifier.verify();
    }

    @Test
    void emitToMissingTopicThrowsTopicNotFound() {
        assertThrows(TopicNotFoundException.class, () ->
            emissionService.emitToTopic(topicManager, "missing", null, "data", null)
        );
    }

    @Test
    void emitWithoutSubscribersMapsToEmissionRejected() {
        // Multicast default with zero subscribers causes tryEmitNext to return FAIL_ZERO_SUBSCRIBER
        TopicChannel ch = topicManager.getOrCreate("t2");
        assertThrows(EmissionRejectedException.class, () ->
            emissionService.emitToTopic(topicManager, "t2", null, "payload", null)
        );
    }

    @Test
    void broadcastSendsToAllActiveTopics() {
        String a = "A", b = "B";
        TopicChannel chA = topicManager.getOrCreate(a);
        TopicChannel chB = topicManager.getOrCreate(b);

        StepVerifier va = StepVerifier.create(chA.sink.asFlux())
                .then(() -> emissionService.broadcast(topicManager, 42))
                .assertNext(sse -> assertEquals(42, sse.data()))
                .thenCancel().verifyLater();

        StepVerifier vb = StepVerifier.create(chB.sink.asFlux())
                .then(() -> emissionService.broadcast(topicManager, 42))
                .assertNext(sse -> assertEquals(42, sse.data()))
                .thenCancel().verifyLater();

        va.verify();
        vb.verify();
    }

    // --- Retry behavior tests ---

    @Test
    void emitWithZeroRetriesStillWorksWithoutContention() {
        // With retries disabled, normal (non-contended) emission should still succeed
        EmissionService noRetry = new EmissionService(null, 0);
        String topic = "no-retry";
        TopicChannel ch = topicManager.getOrCreate(topic);
        Flux<ServerSentEvent<Object>> flux = ch.sink.asFlux();
        StepVerifier verifier = StepVerifier.create(flux).then(() -> {
            noRetry.emitToTopic(topicManager, topic, null, "hello", null);
        }).assertNext(sse -> {
            assertEquals("hello", sse.data());
        }).thenCancel().verifyLater();
        verifier.verify();
    }

    @Test
    void emitRetriesPropertyClampedToMax() {
        SseServerProperties.Emitter emitter = new SseServerProperties.Emitter();

        // Negative values clamped to 0
        emitter.setEmitRetries(-5);
        assertEquals(0, emitter.getEmitRetries());

        // Values above MAX clamped to MAX
        emitter.setEmitRetries(999_999);
        assertEquals(SseServerProperties.Emitter.MAX_EMIT_RETRIES, emitter.getEmitRetries());

        // Normal values pass through
        emitter.setEmitRetries(32);
        assertEquals(32, emitter.getEmitRetries());
    }

    @Test
    void concurrentEmitsToReplayTopicSucceed() throws Exception {
        // Use REPLAY sink to get a serialized sink (which can return FAIL_NON_SERIALIZED)
        SseServerProperties replayProps = new SseServerProperties();
        replayProps.getEmitter().setSinkType(SseServerProperties.SinkType.REPLAY);
        replayProps.getEmitter().setReplaySize(10);
        SinkFactory replaySinkFactory = new SinkFactory(replayProps, null);
        TopicManager replayTopicManager = new TopicManager(replaySinkFactory);
        EmissionService replayService = new EmissionService(null, SseServerProperties.Emitter.DEFAULT_EMIT_RETRIES);

        String topic = "replay-concurrent";
        TopicChannel ch = replayTopicManager.getOrCreate(topic);

        // Subscribe to capture emitted events
        AtomicInteger received = new AtomicInteger(0);
        ch.sink.asFlux().subscribe(sse -> received.incrementAndGet());

        // Hammer the sink from multiple threads to trigger serialization contention
        int threadCount = 4;
        int emitsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await(); // all threads start together
                    for (int i = 0; i < emitsPerThread; i++) {
                        try {
                            replayService.emitToTopic(replayTopicManager, topic, "evt",
                                    "t" + threadId + "-" + i, null);
                        } catch (EmissionRejectedException e) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // fire!
        doneLatch.await();
        executor.shutdown();

        int totalAttempted = threadCount * emitsPerThread;
        int totalDelivered = received.get();

        // With retry logic, the vast majority of emissions should succeed.
        // Allow a small tolerance for edge cases, but failures should be rare.
        assertTrue(totalDelivered >= totalAttempted - failures.get(),
                "Expected most emissions to succeed, but got " + totalDelivered +
                " delivered out of " + totalAttempted + " attempted (" + failures.get() + " failures)");

        // The retry mechanism should prevent most/all serialization failures
        assertTrue(failures.get() <= totalAttempted * 0.05,
                "Too many serialization failures: " + failures.get() + " out of " + totalAttempted);
    }
}
