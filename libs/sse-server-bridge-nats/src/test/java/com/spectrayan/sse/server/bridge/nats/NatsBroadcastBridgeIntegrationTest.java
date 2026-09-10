package com.spectrayan.sse.server.bridge.nats;

import com.spectrayan.sse.server.bridge.SseBridgeMessage;
import io.nats.client.Connection;
import io.nats.client.Nats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for {@link NatsBroadcastBridge} using a real NATS server
 * running inside a Testcontainer.
 * <p>
 * Tests multi-pod cross-instance broadcast fan-out and self-deduplication.
 */
@Testcontainers(disabledWithoutDocker = true)
class NatsBroadcastBridgeIntegrationTest {

    @Container
    static GenericContainer<?> nats = new GenericContainer<>(DockerImageName.parse("nats:alpine"))
            .withExposedPorts(4222);

    private Connection connA;
    private Connection connB;
    private NatsBroadcastBridge bridgeA;
    private NatsBroadcastBridge bridgeB;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() throws Exception {
        String natsUrl = "nats://" + nats.getHost() + ":" + nats.getMappedPort(4222);
        connA = Nats.connect(natsUrl);
        connB = Nats.connect(natsUrl);
        jsonMapper = JsonMapper.builder().build();

        bridgeA = new NatsBroadcastBridge(connA, jsonMapper, "sse.fanout", "pod-a", null);
        bridgeB = new NatsBroadcastBridge(connB, jsonMapper, "sse.fanout", "pod-b", null);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (bridgeA != null) {
            bridgeA.close();
        }
        if (bridgeB != null) {
            bridgeB.close();
        }
        if (connA != null) {
            connA.close();
        }
        if (connB != null) {
            connB.close();
        }
    }

    @Test
    void crossInstanceFanOutAndSelfDeduplication() throws Exception {
        CountDownLatch latchB = new CountDownLatch(1);
        CountDownLatch latchA = new CountDownLatch(1);
        CopyOnWriteArrayList<SseBridgeMessage> receivedByA = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<SseBridgeMessage> receivedByB = new CopyOnWriteArrayList<>();

        bridgeA.subscribe(msg -> {
            receivedByA.add(msg);
            latchA.countDown();
        });

        bridgeB.subscribe(msg -> {
            receivedByB.add(msg);
            latchB.countDown();
        });

        // Event emitted on Pod A
        SseBridgeMessage msgFromA = new SseBridgeMessage(
                "pod-a",
                "orders",
                "order-placed",
                "{\"orderId\":\"12345\"}",
                "evt-100",
                System.currentTimeMillis()
        );

        bridgeA.publish(msgFromA);

        // Pod B should receive within 5 seconds
        assertTrue(latchB.await(5, TimeUnit.SECONDS), "Pod B should have received the message from Pod A");
        assertEquals(1, receivedByB.size());
        assertEquals("orders", receivedByB.get(0).topic());
        assertEquals("order-placed", receivedByB.get(0).eventName());
        assertEquals("evt-100", receivedByB.get(0).id());
        assertEquals("pod-a", receivedByB.get(0).originInstanceId());

        // Pod A should NOT receive its own message (self-deduplication)
        assertFalse(latchA.await(500, TimeUnit.MILLISECONDS), "Pod A should have filtered out its own message");
        assertEquals(0, receivedByA.size());
    }

    @Test
    void bidirectionalFanOut() throws Exception {
        CountDownLatch latchA = new CountDownLatch(1);
        CopyOnWriteArrayList<SseBridgeMessage> receivedByA = new CopyOnWriteArrayList<>();

        bridgeA.subscribe(msg -> {
            receivedByA.add(msg);
            latchA.countDown();
        });

        // Event emitted on Pod B
        SseBridgeMessage msgFromB = new SseBridgeMessage(
                "pod-b",
                "notifications",
                "alert",
                "system-healthy",
                "evt-200",
                System.currentTimeMillis()
        );

        bridgeB.publish(msgFromB);

        assertTrue(latchA.await(5, TimeUnit.SECONDS), "Pod A should have received the message from Pod B");
        assertEquals(1, receivedByA.size());
        assertEquals("notifications", receivedByA.get(0).topic());
        assertEquals("alert", receivedByA.get(0).eventName());
        assertEquals("system-healthy", receivedByA.get(0).payload());
    }
}
