package com.spectrayan.sse.server.metrics;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.topic.TopicRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE-specific Micrometer metrics, auto-registered when Micrometer is on the classpath.
 * <p>
 * Exposed metrics (available at {@code /actuator/prometheus}):
 * <ul>
 *   <li>{@code sse.topics.active} — Gauge: number of currently active topics</li>
 *   <li>{@code sse.subscribers.active} — Gauge: total active subscribers across all topics</li>
 *   <li>{@code sse.events.emitted} — Counter: total events emitted (tagged by result, optionally by topic)</li>
 *   <li>{@code sse.connections} — Counter: total SSE connections opened (optionally tagged by topic)</li>
 * </ul>
 * <p>
 * Per-topic tagging is controlled by {@code spectrayan.sse.server.metrics.per-topic}.
 * Disable it when topic cardinality is very high to avoid excessive time-series in Prometheus.
 */
public class SseMetrics {

    private static final Logger log = LoggerFactory.getLogger(SseMetrics.class);

    private final MeterRegistry meters;
    private final boolean perTopic;

    // Cached per-topic counters to avoid re-registering on every emit
    private final ConcurrentHashMap<String, Counter> emitSuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> emitFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> connectionCounters = new ConcurrentHashMap<>();

    // Global (non-per-topic) counters
    private final Counter globalEmitSuccess;
    private final Counter globalEmitFailure;
    private final Counter globalConnections;
    private final Counter globalDisconnections;

    /**
     * Create SSE metrics and register gauges/counters with the provided MeterRegistry.
     *
     * @param meters         the Micrometer MeterRegistry (auto-injected by Spring Boot)
     * @param topicRegistry  the topic registry for gauge sampling
     * @param properties     SSE server properties (for perTopic flag)
     */
    public SseMetrics(MeterRegistry meters, TopicRegistry topicRegistry, SseServerProperties properties) {
        this.meters = meters;
        this.perTopic = properties.getMetrics().isPerTopic();

        // Gauges — sampled on each Prometheus scrape
        Gauge.builder("sse.topics.active", topicRegistry, r -> r.topics().size())
             .description("Number of active SSE topics")
             .register(meters);

        Gauge.builder("sse.subscribers.active", topicRegistry, r ->
                 r.topicSubscriberCounts().values().stream()
                  .mapToInt(Integer::intValue).sum())
             .description("Total active SSE subscribers across all topics")
             .register(meters);

        // Global counters (always present regardless of perTopic setting)
        this.globalEmitSuccess = Counter.builder("sse.events.emitted")
             .description("Total SSE events emitted")
             .tag("result", "success")
             .register(meters);

        this.globalEmitFailure = Counter.builder("sse.events.emitted")
             .description("Total SSE event emission failures")
             .tag("result", "failure")
             .register(meters);

        this.globalConnections = Counter.builder("sse.connections")
             .description("Total SSE connections opened")
             .register(meters);

        this.globalDisconnections = Counter.builder("sse.disconnections")
             .description("Total SSE connections closed")
             .register(meters);

        log.info("SSE metrics registered (perTopic={})", perTopic);
    }

    /**
     * Record a successful event emission.
     *
     * @param topic the topic the event was emitted to
     */
    public void recordEmit(String topic) {
        globalEmitSuccess.increment();
        if (perTopic && topic != null) {
            emitSuccessCounters.computeIfAbsent(topic, t ->
                Counter.builder("sse.events.emitted.topic")
                       .description("SSE events emitted per topic")
                       .tags(Tags.of("topic", t, "result", "success"))
                       .register(meters)
            ).increment();
        }
    }

    /**
     * Record a failed event emission.
     *
     * @param topic the topic the emission failed for
     */
    public void recordEmitFailure(String topic) {
        globalEmitFailure.increment();
        if (perTopic && topic != null) {
            emitFailureCounters.computeIfAbsent(topic, t ->
                Counter.builder("sse.events.emitted.topic")
                       .description("SSE event emission failures per topic")
                       .tags(Tags.of("topic", t, "result", "failure"))
                       .register(meters)
            ).increment();
        }
    }

    /**
     * Record a new SSE connection.
     *
     * @param topic the topic the client connected to
     */
    public void recordConnection(String topic) {
        globalConnections.increment();
        if (perTopic && topic != null) {
            connectionCounters.computeIfAbsent(topic, t ->
                Counter.builder("sse.connections.topic")
                       .description("SSE connections per topic")
                       .tag("topic", t)
                       .register(meters)
            ).increment();
        }
    }

    /**
     * Record an SSE disconnection.
     *
     * @param topic the topic the client disconnected from
     */
    public void recordDisconnection(String topic) {
        globalDisconnections.increment();
    }

    /**
     * Clean up per-topic counters and remove their Micrometer registrations
     * when a topic is destroyed. Prevents memory leaks when topic names are
     * dynamic (e.g., per-user topics).
     *
     * @param topic the topic being removed
     */
    public void removeTopic(String topic) {
        if (!perTopic || topic == null) return;
        removeAndClose(emitSuccessCounters, topic);
        removeAndClose(emitFailureCounters, topic);
        removeAndClose(connectionCounters, topic);
        log.debug("Cleaned up per-topic metrics for: {}", topic);
    }

    private void removeAndClose(ConcurrentHashMap<String, Counter> map, String topic) {
        Counter counter = map.remove(topic);
        if (counter != null) {
            meters.remove(counter.getId());
        }
    }
}
