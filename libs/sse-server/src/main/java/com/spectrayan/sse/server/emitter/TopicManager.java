package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.topic.TopicRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages lifecycle and registry of SSE topics and exposes read-only views for monitoring.
 * <p>
 * Responsibilities:
 * - Lazily create {@link TopicChannel} on first access using {@link SinkFactory}.
 * - Provide lookup and removal of topics; remove is invoked by {@link SessionTracker} when appropriate.
 * - Expose {@link com.spectrayan.sse.server.topic.TopicRegistry} read-only projections: topic ids,
 *   subscriber counts, and per-topic session maps.
 * - Perform graceful shutdown by completing all sinks and clearing the registry.
 * <p>
 * Package-private; used by {@link AbstractSseEmitter} and collaborators to keep responsibilities focused.
 */
final class TopicManager implements TopicRegistry {

    private final ConcurrentHashMap<String, TopicChannel> topics = new ConcurrentHashMap<>();
    private final SinkFactory sinkFactory;
    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TopicManager.class);

    /**
     * Create a new {@code TopicManager} using the provided {@link SinkFactory} for
     * per-topic sink creation on first access.
     *
     * @param sinkFactory factory used to create {@link reactor.core.publisher.Sinks.Many}
     *                    for new topics
     */
    TopicManager(SinkFactory sinkFactory) {
        this.sinkFactory = sinkFactory;
    }

    /**
     * Get an existing {@link TopicChannel} for the given topic or create it lazily if absent.
     * <p>
     * Creation logs an info message and initializes the channel's sink via {@link SinkFactory}.
     *
     * @param topic topic identifier; must not be {@code null}
     * @return existing or newly created channel
     */
    TopicChannel getOrCreate(String topic) {
        return topics.computeIfAbsent(topic, id -> {
            log.info("Creating SSE topic: {}", id);
            return new TopicChannel(sinkFactory.create(id));
        });
    }

    /**
     * Lookup the channel for a topic without creating it.
     *
     * @param topic topic identifier
     * @return the channel or {@code null} if not present
     */
    TopicChannel get(String topic) {
        return topics.get(topic);
    }

    /**
     * Remove a topic from the registry without completing its sink.
     * Intended to be invoked by {@link SessionTracker} once the last subscriber
     * has left due to cancel/error. Completion of the sink is performed by the
     * tracker before removal.
     *
     * @param topic topic identifier to remove
     */
    void remove(String topic) {
        topics.remove(topic);
    }

    /**
     * Return a snapshot of currently active topic identifiers.
     *
     * @return immutable copy of topic ids
     */
    @Override
    public Collection<String> topics() {
        return List.copyOf(topics.keySet());
    }

    /**
     * Expose the current sessions map for a topic as an unmodifiable copy.
     *
     * @param topic topic identifier
     * @return immutable copy of sessionId -> {@link com.spectrayan.sse.server.session.SseSession}; empty if topic missing
     */
    @Override
    public Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) {
        TopicChannel t = topics.get(topic);
        if (t == null) return java.util.Collections.emptyMap();
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(t.sessions));
    }

    /**
     * Return the current subscriber count for a topic.
     *
     * @param topic topic identifier
     * @return number of active subscribers (0 if topic missing)
     */
    @Override
    public int subscriberCount(String topic) {
        TopicChannel t = topics.get(topic);
        return t != null ? t.subscribers.get() : 0;
    }

    /**
     * Return a snapshot mapping of topic id -> subscriber count.
     * The returned map is an immutable copy.
     *
     * @return immutable map of subscriber counts per topic
     */
    @Override
    public Map<String, Integer> topicSubscriberCounts() {
        Map<String, Integer> m = new LinkedHashMap<>();
        topics.forEach((id, t) -> m.put(id, t.subscribers.get()));
        return java.util.Collections.unmodifiableMap(m);
    }

    /**
     * Gracefully complete all topic sinks and clear the registry.
     * <p>
     * For each topic, {@code tryEmitComplete()} is invoked and any errors are logged.
     * After completion attempts, the internal map is cleared.
     */
    void shutdownAll() {
        int count = topics.size();
        if (count > 0) {
            log.info("Shutting down SSE topics: completing {} channel(s)", count);
        } else {
            log.info("Shutting down SSE topics: no active channels");
        }
        topics.forEach((id, ch) -> {
            try {
                ch.sink.tryEmitComplete();
            } catch (Throwable t) {
                log.warn("Error completing SSE channel for topic {}: {}", id, t.getMessage());
            }
        });
        topics.clear();
    }
}
