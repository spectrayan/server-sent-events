package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.template.SseConnectContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DefaultReconnectPolicyTest {

    private static SseConnectContext ctx() {
        return new SseConnectContext("orders", "sid-1", null, "remote", java.util.Map.of(), java.util.Map.of());
    }

    @Test
    void returnsMillisWhenRetryEnabled() {
        SseServerProperties props = new SseServerProperties();
        props.getStream().setRetryEnabled(true);
        props.getStream().setRetry(Duration.ofSeconds(5));
        DefaultReconnectPolicy p = new DefaultReconnectPolicy(props);
        assertTrue(p.retryDelayMillis(ctx()).isPresent());
        assertEquals(5000L, p.retryDelayMillis(ctx()).get());
    }

    @Test
    void emptyWhenDisabled() {
        SseServerProperties props = new SseServerProperties();
        props.getStream().setRetryEnabled(false);
        props.getStream().setRetry(Duration.ofSeconds(1));
        DefaultReconnectPolicy p = new DefaultReconnectPolicy(props);
        assertTrue(p.retryDelayMillis(ctx()).isEmpty());
    }
}
