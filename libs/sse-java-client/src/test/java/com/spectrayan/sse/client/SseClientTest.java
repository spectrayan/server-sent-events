package com.spectrayan.sse.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SseClientTest {

    private MockWebServer server;
    private SseClient client;

    public record Notification(String id, String text) {}

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        client = SseClient.builder()
            .baseUrl(server.url("/").toString())
            .reconnection(SseReconnectionConfig.builder()
                .enabled(false)
                .build())
            .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldStreamAndDeserializeJsonPayloads() throws InterruptedException {
        String ssePayload = "event: alert\nid: 101\ndata: {\"id\":\"notif-1\",\"text\":\"Order placed\"}\n\n";
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(ssePayload));

        StepVerifier.create(client.stream(Notification.class))
            .assertNext(notification -> {
                assertThat(notification.id()).isEqualTo("notif-1");
                assertThat(notification.text()).isEqualTo("Order placed");
            })
            .verifyComplete();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Accept")).contains("text/event-stream");
    }

    @Test
    void shouldFilterByEventType() {
        String ssePayload = "event: ping\ndata: keepalive\n\nevent: order\ndata: {\"id\":\"ord-2\",\"text\":\"Shipped\"}\n\n";
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(ssePayload));

        StepVerifier.create(client.stream("order", Notification.class))
            .assertNext(notification -> {
                assertThat(notification.id()).isEqualTo("ord-2");
                assertThat(notification.text()).isEqualTo("Shipped");
            })
            .verifyComplete();
    }

    @Test
    void shouldStreamFullSseEventMetadata() {
        String ssePayload = "id: evt-999\nevent: custom\nretry: 5000\ndata: {\"id\":\"1\",\"text\":\"hello\"}\n\n";
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(ssePayload));

        StepVerifier.create(client.streamEvents(Notification.class))
            .assertNext(event -> {
                assertThat(event.id()).isEqualTo("evt-999");
                assertThat(event.event()).isEqualTo("custom");
                assertThat(event.retry()).isEqualTo(Duration.ofMillis(5000));
                assertThat(event.data().id()).isEqualTo("1");
                assertThat(event.rawData()).contains("hello");
            })
            .verifyComplete();
    }

    @Test
    void shouldReconnectWithLastEventIdHeader() throws InterruptedException {
        SseClient reconnectClient = SseClient.builder()
            .baseUrl(server.url("/").toString())
            .reconnection(SseReconnectionConfig.builder()
                .enabled(true)
                .initialDelay(Duration.ofMillis(20))
                .maxDelay(Duration.ofMillis(50))
                .jitter(0.0)
                .maxRetries(2L)
                .build())
            .build();

        // First response delivers an event with id: evt-1, then terminates abruptly
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody("id: evt-1\ndata: {\"id\":\"first\",\"text\":\"message\"}\n\n")
            .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

        // Second response simulates connection re-establishment
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody("id: evt-2\ndata: {\"id\":\"second\",\"text\":\"message\"}\n\n"));

        StepVerifier.create(reconnectClient.stream(Notification.class).take(2))
            .assertNext(notif -> assertThat(notif.id()).isEqualTo("first"))
            .assertNext(notif -> assertThat(notif.id()).isEqualTo("second"))
            .verifyComplete();

        RecordedRequest req1 = server.takeRequest();
        assertThat(req1.getHeader("Last-Event-ID")).isNull();

        RecordedRequest req2 = server.takeRequest();
        assertThat(req2.getHeader("Last-Event-ID")).isEqualTo("evt-1");
    }
}