package com.spectrayan.sse.server.bridge.redis;

import com.spectrayan.sse.server.bridge.SseBroadcastListener;
import com.spectrayan.sse.server.bridge.SseBridgeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RedisBroadcastBridge}.
 * <p>
 * These tests verify self-deduplication, listener delegation, and edge cases
 * via the {@code handleIncoming} method, without requiring a running Redis instance.
 */
class RedisBroadcastBridgeTest {

    private static final String INSTANCE_A = "instance-a";
    private static final String INSTANCE_B = "instance-b";

    private RedisBroadcastBridge createBridge(String instanceId) {
        var redisTemplate = mock(ReactiveStringRedisTemplate.class);
        // Mock listenTo to return empty Flux (no real Redis connection)
        when(redisTemplate.listenTo(any(org.springframework.data.redis.listener.ChannelTopic.class)))
                .thenReturn(reactor.core.publisher.Flux.empty());
        var jsonMapper = JsonMapper.builder().build();
        return new RedisBroadcastBridge(redisTemplate, jsonMapper, "sse-broadcast", instanceId);
    }

    @Test
    void handleIncomingSkipsSelfOriginatedMessages() {
        var bridge = createBridge(INSTANCE_A);

        var listener = mock(SseBroadcastListener.class);
        bridge.subscribe(listener);

        var selfMsg = new SseBridgeMessage(INSTANCE_A, "topic1", "evt", "data", "id1", System.currentTimeMillis());
        bridge.handleIncoming(selfMsg);

        verifyNoInteractions(listener);
    }

    @Test
    void handleIncomingDelegatesToListenerForRemoteMessages() {
        var bridge = createBridge(INSTANCE_A);

        var listener = mock(SseBroadcastListener.class);
        bridge.subscribe(listener);

        var remoteMsg = new SseBridgeMessage(INSTANCE_B, "topic1", "evt", "payload", "id2", System.currentTimeMillis());
        bridge.handleIncoming(remoteMsg);

        verify(listener, times(1)).onRemoteEvent(remoteMsg);
    }

    @Test
    void handleIncomingWithNoListenerIsNoOp() {
        var bridge = createBridge(INSTANCE_A);

        var msg = new SseBridgeMessage(INSTANCE_B, "topic1", null, "data", null, System.currentTimeMillis());
        assertDoesNotThrow(() -> bridge.handleIncoming(msg));
    }

    @Test
    void closeNullsOutListener() {
        var bridge = createBridge(INSTANCE_A);
        var listener = mock(SseBroadcastListener.class);

        bridge.subscribe(listener);
        bridge.close();

        var msg = new SseBridgeMessage(INSTANCE_B, "topic1", null, "data", null, System.currentTimeMillis());
        bridge.handleIncoming(msg);
        verifyNoInteractions(listener);
    }

    @Test
    void listenerExceptionDoesNotPropagate() {
        var bridge = createBridge(INSTANCE_A);

        var listener = mock(SseBroadcastListener.class);
        doThrow(new RuntimeException("boom")).when(listener).onRemoteEvent(any());
        bridge.subscribe(listener);

        var msg = new SseBridgeMessage(INSTANCE_B, "topic1", null, "data", null, System.currentTimeMillis());
        assertDoesNotThrow(() -> bridge.handleIncoming(msg));
    }

    @Test
    void subscribeRegistersListener() {
        var bridge = createBridge(INSTANCE_A);
        var listener = mock(SseBroadcastListener.class);

        bridge.subscribe(listener);

        var msg = new SseBridgeMessage(INSTANCE_B, "topic1", null, "data", null, System.currentTimeMillis());
        bridge.handleIncoming(msg);
        verify(listener, times(1)).onRemoteEvent(msg);
    }
}
