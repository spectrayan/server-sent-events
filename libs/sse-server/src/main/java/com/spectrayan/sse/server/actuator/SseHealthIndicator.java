package com.spectrayan.sse.server.actuator;

import com.spectrayan.sse.server.bridge.NoOpBroadcastBridge;
import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.topic.TopicRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Reactive health indicator reporting SSE server and broadcast bridge status
 * under {@code /actuator/health/sse}.
 *
 * @since 2.1.0
 */
public class SseHealthIndicator implements ReactiveHealthIndicator {

    private final TopicRegistry topicRegistry;
    private final SseBroadcastBridge broadcastBridge;

    public SseHealthIndicator(TopicRegistry topicRegistry, SseBroadcastBridge broadcastBridge) {
        this.topicRegistry = Objects.requireNonNull(topicRegistry, "topicRegistry must not be null");
        this.broadcastBridge = broadcastBridge;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(() -> {
            try {
                Collection<String> topics = topicRegistry.topics();
                Map<String, Integer> subscriberCounts = topicRegistry.topicSubscriberCounts();
                int totalSubscribers = subscriberCounts != null
                        ? subscriberCounts.values().stream().mapToInt(Integer::intValue).sum()
                        : 0;

                String bridgeName = broadcastBridge != null
                        ? broadcastBridge.getClass().getSimpleName()
                        : "None";
                boolean isClustered = broadcastBridge != null && !(broadcastBridge instanceof NoOpBroadcastBridge);

                return Health.up()
                        .withDetail("activeTopics", topics != null ? topics.size() : 0)
                        .withDetail("totalSubscribers", totalSubscribers)
                        .withDetail("bridge", bridgeName)
                        .withDetail("clustered", isClustered)
                        .build();
            } catch (Exception e) {
                return Health.down(e)
                        .withDetail("error", e.getMessage() != null ? e.getMessage() : "Unknown error during SSE health evaluation")
                        .build();
            }
        });
    }
}
