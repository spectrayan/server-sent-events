package com.spectrayan.sse.server.actuator;

import com.spectrayan.sse.server.bridge.NoOpBroadcastBridge;
import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.topic.TopicRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SseHealthIndicatorTest {

    @Test
    void healthUpWithDefaultNoOpBridgeAndEmptyTopics() {
        TopicRegistry registry = mock(TopicRegistry.class);
        when(registry.topics()).thenReturn(List.of());
        when(registry.topicSubscriberCounts()).thenReturn(Map.of());

        SseHealthIndicator indicator = new SseHealthIndicator(registry, new NoOpBroadcastBridge());

        StepVerifier.create(indicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.UP);
                    assertThat(health.getDetails())
                            .containsEntry("activeTopics", 0)
                            .containsEntry("totalSubscribers", 0)
                            .containsEntry("bridge", "NoOpBroadcastBridge")
                            .containsEntry("clustered", false);
                })
                .verifyComplete();
    }

    @Test
    void healthUpWithActiveTopicsAndSubscribers() {
        TopicRegistry registry = mock(TopicRegistry.class);
        when(registry.topics()).thenReturn(List.of("orders", "notifications"));
        when(registry.topicSubscriberCounts()).thenReturn(Map.of("orders", 12, "notifications", 5));

        SseBroadcastBridge mockBridge = mock(SseBroadcastBridge.class);

        SseHealthIndicator indicator = new SseHealthIndicator(registry, mockBridge);

        StepVerifier.create(indicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.UP);
                    assertThat(health.getDetails())
                            .containsEntry("activeTopics", 2)
                            .containsEntry("totalSubscribers", 17)
                            .containsEntry("clustered", true);
                })
                .verifyComplete();
    }

    @Test
    void healthDownWhenTopicRegistryThrows() {
        TopicRegistry registry = mock(TopicRegistry.class);
        when(registry.topics()).thenThrow(new RuntimeException("Registry failure"));

        SseHealthIndicator indicator = new SseHealthIndicator(registry, new NoOpBroadcastBridge());

        StepVerifier.create(indicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                    assertThat(health.getDetails()).containsKey("error");
                    assertThat(health.getDetails().get("error").toString()).contains("Registry failure");
                })
                .verifyComplete();
    }
}
