package com.spectrayan.sse.server.bridge.cloudstream;

import com.spectrayan.sse.server.bridge.SseBroadcastListener;
import com.spectrayan.sse.server.bridge.SseBridgeMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CloudStreamBroadcastBridge}.
 * <p>
 * These tests verify self-deduplication, listener delegation, and edge cases
 * without requiring a running broker or Spring context.
 */
class CloudStreamBroadcastBridgeTest {

    private static final String INSTANCE_A = "instance-a";
    private static final String INSTANCE_B = "instance-b";

    @Test
    void handleIncomingSkipsSelfOriginatedMessages() {
        var streamBridge = mock(org.springframework.cloud.stream.function.StreamBridge.class);
        var bridge = new CloudStreamBroadcastBridge(streamBridge, INSTANCE_A, "sse-broadcast");

        var listener = mock(SseBroadcastListener.class);
        bridge.subscribe(listener);

        // Message from the same instance should be skipped
        var selfMsg = new SseBridgeMessage(INSTANCE_A, "topic1", "evt", "data", "id1", System.currentTimeMillis());
        bridge.handleIncoming(selfMsg);

        verifyNoInteractions(listener);
    }

    @Test
    void handleIncomingDelegatesToListenerForRemoteMessages() {
        var streamBridge = mock(org.springframework.cloud.stream.function.StreamBridge.class);
        var bridge = new CloudStreamBroadcastBridge(streamBridge, INSTANCE_A, "sse-broadcast");

        var listener = mock(SseBroadcastListener.class);
        bridge.subscribe(listener);

        // Message from a different instance should be forwarded
        var remoteMsg = new SseBridgeMessage(INSTANCE_B, "topic1", "evt", "payload", "id2", System.currentTimeMillis());
        bridge.handleIncoming(remoteMsg);

        verify(listener, times(1)).onRemoteEvent(remoteMsg);
    }

    @Test
    void handleIncomingWithNoListenerIsNoOp() {
        var streamBridge = mock(org.springframework.cloud.stream.function.StreamBridge.class);
        var bridge = new CloudStreamBroadcastBridge(streamBridge, INSTANCE_A, "sse-broadcast");

        // No listener registered — should not throw
        var msg = new SseBridgeMessage(INSTANCE_B, "topic1", null, "data", null, System.currentTimeMillis());
        assertDoesNotThrow(() -> bridge.handleIncoming(msg));
    }

    @Test
    void publishSendsViaStreamBridge() {
        var streamBridge = mock(org.springframework.cloud.stream.function.StreamBridge.class);
        when(streamBridge.send(anyString(), any())).thenReturn(true);

        var bridge = new CloudStreamBroadcastBridge(streamBridge, INSTANCE_A, "sse-broadcast");

        var msg = new SseBridgeMessage(INSTANCE_A, "topic1", "evt", "data", "id1", System.currentTimeMillis());
        bridge.publish(msg);

        verify(streamBridge, times(1)).send(eq("sse-broadcast"), any(org.springframework.messaging.Message.class));
    }

    @Test
    void publishLogsWarningOnSendFailure() {
        var streamBridge = mock(org.springframework.cloud.stream.function.StreamBridge.class);
        when(streamBridge.send(anyString(), any())).thenReturn(false);

        var bridge = new CloudStreamBroadcastBridge(streamBridge, INSTANCE_A, "sse-broadcast");

        var msg = new SseBridgeMessage(INSTANCE_A, "topic1", "evt", "data", "id1", System.currentTimeMillis());
        // Should not throw, just log a warning
        assertDoesNotThrow(() -> bridge.publish(msg));
        verify(streamBridge, times(1)).send(eq("sse-broadcast"), any());
    }

    @Test
    void closeNullsOutListener() {
        var streamBridge = mock(org.springframework.cloud.stream.function.StreamBridge.class);
        var bridge = new CloudStreamBroadcastBridge(streamBridge, INSTANCE_A, "sse-broadcast");
        var listener = mock(SseBroadcastListener.class);

        bridge.subscribe(listener);
        bridge.close();

        // After close, incoming messages should be ignored (no listener)
        var msg = new SseBridgeMessage(INSTANCE_B, "topic1", null, "data", null, System.currentTimeMillis());
        bridge.handleIncoming(msg);
        verifyNoInteractions(listener);
    }

    @Test
    void listenerExceptionDoesNotPropagate() {
        var streamBridge = mock(org.springframework.cloud.stream.function.StreamBridge.class);
        var bridge = new CloudStreamBroadcastBridge(streamBridge, INSTANCE_A, "sse-broadcast");

        var listener = mock(SseBroadcastListener.class);
        doThrow(new RuntimeException("boom")).when(listener).onRemoteEvent(any());
        bridge.subscribe(listener);

        var msg = new SseBridgeMessage(INSTANCE_B, "topic1", null, "data", null, System.currentTimeMillis());
        // Exception in listener should be caught, not propagated
        assertDoesNotThrow(() -> bridge.handleIncoming(msg));
    }
}
