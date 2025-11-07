package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class StreamComposerTest {

    private SseServerProperties props;

    @BeforeEach
    void setUp() {
        props = new SseServerProperties();
        // Make intervals short for tests
        props.getStream().setHeartbeatEnabled(true);
        props.getStream().setHeartbeatInterval(Duration.ofSeconds(1));
        props.getStream().setConnectedEventEnabled(true);
        props.getStream().setConnectedEventName("connected");
        props.getStream().setConnectedEventData("hello");
        props.getStream().setHeartbeatEventName("heartbeat");
        props.getStream().setHeartbeatData("::hb::");
    }

    @Test
    void prependsConnectedEventAndEmitsHeartbeats() {
        StreamComposer composer = new StreamComposer(props);
        StepVerifier.withVirtualTime(() -> composer.compose("topic", Flux.never()))
            .thenAwait(Duration.ZERO)
            .expectNextMatches(ev -> "connected".equals(ev.event()) && "hello".equals(ev.data()))
            .thenAwait(Duration.ofSeconds(3))
            .expectNextMatches(ev -> "heartbeat".equals(ev.event()) && "::hb::".equals(ev.data()))
            .expectNextMatches(ev -> "heartbeat".equals(ev.event()) && "::hb::".equals(ev.data()))
            .expectNextMatches(ev -> "heartbeat".equals(ev.event()) && "::hb::".equals(ev.data()))
            .thenCancel()
            .verify();
    }

    @Test
    void connectedEventDisabledOnlyHeartbeats() {
        props.getStream().setConnectedEventEnabled(false);
        StreamComposer composer = new StreamComposer(props);

        StepVerifier.withVirtualTime(() -> composer.compose("topic", Flux.never()))
            .thenAwait(Duration.ofSeconds(2))
            .expectNextMatches(ev -> "heartbeat".equals(ev.event()))
            .expectNextMatches(ev -> "heartbeat".equals(ev.event()))
            .thenCancel()
            .verify();
    }
}
