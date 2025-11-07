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
        emissionService = new EmissionService();
    }

    @Test
    void emitToExistingTopicDeliversEvent() {
        String topic = "t1";
        TopicChannel ch = topicManager.getOrCreate(topic);
        Flux<ServerSentEvent<Object>> flux = ch.sink.asFlux();
        StepVerifier verifier = StepVerifier.create(flux).then(() -> {
            emissionService.emitToTopic(topicManager, topic, "evt", List.of(1,2,3), "id-1");
        }).assertNext(sse -> {
            org.junit.jupiter.api.Assertions.assertEquals("evt", sse.event());
            org.junit.jupiter.api.Assertions.assertEquals("id-1", sse.id());
            org.junit.jupiter.api.Assertions.assertEquals(List.of(1,2,3), sse.data());
        }).thenCancel().verifyLater();
        verifier.verify();
    }

    @Test
    void emitToMissingTopicThrowsTopicNotFound() {
        org.junit.jupiter.api.Assertions.assertThrows(TopicNotFoundException.class, () ->
            emissionService.emitToTopic(topicManager, "missing", null, "data", null)
        );
    }

    @Test
    void emitWithoutSubscribersMapsToEmissionRejected() {
        // Multicast default with zero subscribers causes tryEmitNext to return FAIL_ZERO_SUBSCRIBER
        TopicChannel ch = topicManager.getOrCreate("t2");
        org.junit.jupiter.api.Assertions.assertThrows(EmissionRejectedException.class, () ->
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
                .assertNext(sse -> org.junit.jupiter.api.Assertions.assertEquals(42, sse.data()))
                .thenCancel().verifyLater();

        StepVerifier vb = StepVerifier.create(chB.sink.asFlux())
                .then(() -> emissionService.broadcast(topicManager, 42))
                .assertNext(sse -> org.junit.jupiter.api.Assertions.assertEquals(42, sse.data()))
                .thenCancel().verifyLater();

        va.verify();
        vb.verify();
    }
}
