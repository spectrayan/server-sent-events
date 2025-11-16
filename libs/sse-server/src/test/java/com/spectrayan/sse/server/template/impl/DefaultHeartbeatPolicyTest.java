package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.template.EventSerializer;
import com.spectrayan.sse.server.template.SseConnectContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DefaultHeartbeatPolicyTest {

    @Test
    void emitsHeartbeatsAtConfiguredInterval() {
        SseServerProperties props = new SseServerProperties();
        props.getStream().setHeartbeatEnabled(true);
        props.getStream().setHeartbeatInterval(Duration.ofSeconds(5));
        props.getStream().setHeartbeatEventName("heartbeat");
        props.getStream().setHeartbeatData("::hb::");

        EventSerializer ser = new DefaultEventSerializer();
        DefaultHeartbeatPolicy policy = new DefaultHeartbeatPolicy(props, ser);
        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", java.util.Map.of(), java.util.Map.of());

        StepVerifier.withVirtualTime(() -> policy.heartbeats(ctx))
                .thenAwait(Duration.ofSeconds(5))
                .assertNext(sse -> {
                    assertEquals("heartbeat", sse.event());
                    assertEquals("::hb::", sse.data());
                })
                .thenAwait(Duration.ofSeconds(5))
                .assertNext(sse -> assertEquals("::hb::", sse.data()))
                .thenCancel()
                .verify();
    }

    @Test
    void disabledEmitsNever() {
        SseServerProperties props = new SseServerProperties();
        props.getStream().setHeartbeatEnabled(false);
        EventSerializer ser = new DefaultEventSerializer();
        DefaultHeartbeatPolicy policy = new DefaultHeartbeatPolicy(props, ser);
        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", java.util.Map.of(), java.util.Map.of());

        StepVerifier.withVirtualTime(() -> policy.heartbeats(ctx))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(10))
                .thenCancel()
                .verify();
    }
}
