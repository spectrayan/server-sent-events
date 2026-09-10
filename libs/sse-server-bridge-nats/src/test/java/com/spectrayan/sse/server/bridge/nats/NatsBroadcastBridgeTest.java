package com.spectrayan.sse.server.bridge.nats;

import com.spectrayan.sse.server.bridge.SseBridgeMessage;
import com.spectrayan.sse.server.bridge.SseBroadcastListener;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NatsBroadcastBridge}.
 * <p>
 * Verifies self-deduplication, remote listener dispatching, publishing,
 * queue group subscription, and lifecycle cleanup without requiring a live NATS broker.
 */
class NatsBroadcastBridgeTest {

    private static final String INSTANCE_A = "pod-a";
    private static final String INSTANCE_B = "pod-b";
    private static final String SUBJECT = "sse.broadcast";

    private Connection connection;
    private Dispatcher dispatcher;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        connection = mock(Connection.class);
        dispatcher = mock(Dispatcher.class);
        when(connection.createDispatcher(any())).thenReturn(dispatcher);
        jsonMapper = JsonMapper.builder().build();
    }

    private NatsBroadcastBridge createBridge(String instanceId) {
        return new NatsBroadcastBridge(connection, jsonMapper, SUBJECT, instanceId, null);
    }

    private NatsBroadcastBridge createBridge(String instanceId, String queueGroup) {
        return new NatsBroadcastBridge(connection, jsonMapper, SUBJECT, instanceId, queueGroup);
    }

    @Test
    void initializesWithBroadcastSubscriptionWhenQueueGroupIsNull() {
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);

        assertEquals(SUBJECT, bridge.getSubject());
        assertEquals(INSTANCE_A, bridge.getInstanceId());
        assertNull(bridge.getQueueGroup());

        verify(connection).createDispatcher(any(MessageHandler.class));
        verify(dispatcher).subscribe(SUBJECT);
        verify(dispatcher, never()).subscribe(anyString(), anyString());
    }

    @Test
    void initializesWithQueueGroupSubscriptionWhenQueueGroupProvided() {
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A, "my-group");

        assertEquals("my-group", bridge.getQueueGroup());
        verify(dispatcher).subscribe(SUBJECT, "my-group");
    }

    @Test
    void blanksQueueGroupTreatedAsBroadcast() {
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A, "   ");

        assertNull(bridge.getQueueGroup());
        verify(dispatcher).subscribe(SUBJECT);
    }

    @Test
    void handleIncomingSkipsSelfOriginatedMessages() {
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);
        SseBroadcastListener listener = mock(SseBroadcastListener.class);
        bridge.subscribe(listener);

        SseBridgeMessage selfMsg = new SseBridgeMessage(INSTANCE_A, "orders", "created", "{}", "msg-1", System.currentTimeMillis());
        bridge.handleIncoming(selfMsg);

        verifyNoInteractions(listener);
    }

    @Test
    void handleIncomingDelegatesToListenerForRemoteMessages() {
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);
        SseBroadcastListener listener = mock(SseBroadcastListener.class);
        bridge.subscribe(listener);

        SseBridgeMessage remoteMsg = new SseBridgeMessage(INSTANCE_B, "orders", "created", "{\"id\":1}", "msg-2", System.currentTimeMillis());
        bridge.handleIncoming(remoteMsg);

        verify(listener, times(1)).onRemoteEvent(remoteMsg);
    }

    @Test
    void handleIncomingWithNoListenerIsNoOp() {
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);

        SseBridgeMessage remoteMsg = new SseBridgeMessage(INSTANCE_B, "orders", "created", "{}", "msg-3", System.currentTimeMillis());
        assertDoesNotThrow(() -> bridge.handleIncoming(remoteMsg));
    }

    @Test
    void listenerExceptionDoesNotPropagate() {
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);
        SseBroadcastListener listener = mock(SseBroadcastListener.class);
        doThrow(new RuntimeException("listener failure")).when(listener).onRemoteEvent(any());
        bridge.subscribe(listener);

        SseBridgeMessage remoteMsg = new SseBridgeMessage(INSTANCE_B, "orders", null, "test", null, System.currentTimeMillis());
        assertDoesNotThrow(() -> bridge.handleIncoming(remoteMsg));
    }

    @Test
    void publishSerializesAndSendsToNatsSubject() throws Exception {
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);

        SseBridgeMessage message = new SseBridgeMessage(INSTANCE_A, "alerts", "ping", "data", "id-1", 123456789L);
        bridge.publish(message);

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(connection).publish(eq(SUBJECT), bytesCaptor.capture());

        byte[] publishedBytes = bytesCaptor.getValue();
        assertNotNull(publishedBytes);

        SseBridgeMessage deserialized = jsonMapper.readValue(publishedBytes, SseBridgeMessage.class);
        assertEquals(INSTANCE_A, deserialized.originInstanceId());
        assertEquals("alerts", deserialized.topic());
        assertEquals("ping", deserialized.eventName());
        assertEquals("data", deserialized.payload());
        assertEquals("id-1", deserialized.id());
        assertEquals(123456789L, deserialized.timestamp());
    }

    @Test
    void publishHandlesConnectionExceptionGracefully() {
        doThrow(new IllegalStateException("connection closed")).when(connection).publish(anyString(), any(byte[].class));
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);

        SseBridgeMessage message = new SseBridgeMessage(INSTANCE_A, "alerts", null, "test", null, System.currentTimeMillis());
        assertDoesNotThrow(() -> bridge.publish(message));
    }

    @Test
    void closeClosesDispatcherAndRemovesListener() {
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);
        SseBroadcastListener listener = mock(SseBroadcastListener.class);
        bridge.subscribe(listener);

        bridge.close();

        verify(connection).closeDispatcher(dispatcher);

        // After close, incoming messages should not reach listener
        SseBridgeMessage remoteMsg = new SseBridgeMessage(INSTANCE_B, "alerts", null, "test", null, System.currentTimeMillis());
        bridge.handleIncoming(remoteMsg);
        verifyNoInteractions(listener);
    }

    @Test
    void closeHandlesDispatcherExceptionGracefully() {
        doThrow(new RuntimeException("error closing")).when(connection).closeDispatcher(any());
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);

        assertDoesNotThrow(bridge::close);
    }

    @Test
    void dispatcherCallbackProcessesValidMessage() throws Exception {
        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);
        verify(connection).createDispatcher(handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

        SseBroadcastListener listener = mock(SseBroadcastListener.class);
        bridge.subscribe(listener);

        SseBridgeMessage remoteMsg = new SseBridgeMessage(INSTANCE_B, "topic1", "evt", "hello", "1", System.currentTimeMillis());
        byte[] payload = jsonMapper.writeValueAsBytes(remoteMsg);

        Message natsMsg = mock(Message.class);
        when(natsMsg.getData()).thenReturn(payload);

        handler.onMessage(natsMsg);

        verify(listener, times(1)).onRemoteEvent(any(SseBridgeMessage.class));
    }

    @Test
    void dispatcherCallbackIgnoresEmptyOrCorruptData() {
        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        NatsBroadcastBridge bridge = createBridge(INSTANCE_A);
        verify(connection).createDispatcher(handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

        SseBroadcastListener listener = mock(SseBroadcastListener.class);
        bridge.subscribe(listener);

        // Empty data
        Message emptyMsg = mock(Message.class);
        when(emptyMsg.getData()).thenReturn(new byte[0]);
        assertDoesNotThrow(() -> handler.onMessage(emptyMsg));

        // Corrupt data
        Message corruptMsg = mock(Message.class);
        when(corruptMsg.getData()).thenReturn("not valid json".getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> handler.onMessage(corruptMsg));

        verifyNoInteractions(listener);
    }

    @Test
    void simulatedMultiPodFanOutBetweenTwoInstances() {
        // Mock connection and dispatcher for Pod A
        Connection connA = mock(Connection.class);
        Dispatcher dispA = mock(Dispatcher.class);
        ArgumentCaptor<MessageHandler> handlerCaptorA = ArgumentCaptor.forClass(MessageHandler.class);
        when(connA.createDispatcher(handlerCaptorA.capture())).thenReturn(dispA);

        // Mock connection and dispatcher for Pod B
        Connection connB = mock(Connection.class);
        Dispatcher dispB = mock(Dispatcher.class);
        ArgumentCaptor<MessageHandler> handlerCaptorB = ArgumentCaptor.forClass(MessageHandler.class);
        when(connB.createDispatcher(handlerCaptorB.capture())).thenReturn(dispB);

        NatsBroadcastBridge bridgePodA = new NatsBroadcastBridge(connA, jsonMapper, SUBJECT, INSTANCE_A, null);
        NatsBroadcastBridge bridgePodB = new NatsBroadcastBridge(connB, jsonMapper, SUBJECT, INSTANCE_B, null);

        MessageHandler handlerA = handlerCaptorA.getValue();
        MessageHandler handlerB = handlerCaptorB.getValue();

        // Simulate broker routing: when either pod publishes, broker delivers to all dispatchers
        doAnswer(invocation -> {
            byte[] data = invocation.getArgument(1);
            Message mockMsg = mock(Message.class);
            when(mockMsg.getData()).thenReturn(data);
            handlerA.onMessage(mockMsg);
            handlerB.onMessage(mockMsg);
            return null;
        }).when(connA).publish(eq(SUBJECT), any(byte[].class));

        SseBroadcastListener listenerA = mock(SseBroadcastListener.class);
        SseBroadcastListener listenerB = mock(SseBroadcastListener.class);
        bridgePodA.subscribe(listenerA);
        bridgePodB.subscribe(listenerB);

        // Pod A publishes an event
        SseBridgeMessage msgFromA = new SseBridgeMessage(INSTANCE_A, "alerts", "fire", "payload", "id-99", 1000L);
        bridgePodA.publish(msgFromA);

        // Pod B must receive it
        verify(listenerB, times(1)).onRemoteEvent(msgFromA);
        // Pod A must have filtered it out (self-deduplication)
        verifyNoInteractions(listenerA);
    }
}

