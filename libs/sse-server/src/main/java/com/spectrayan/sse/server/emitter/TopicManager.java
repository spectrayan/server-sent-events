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

    TopicManager(SinkFactory sinkFactory) {
        this.sinkFactory = sinkFactory;
    }

    TopicChannel getOrCreate(String topic) {
        return topics.computeIfAbsent(topic, id -> {
            log.info("Creating SSE topic: {}", id);
            return new TopicChannel(sinkFactory.create(id));
        });
    }

    TopicChannel get(String topic) {
        return topics.get(topic);
    }

    void remove(String topic) {
        topics.remove(topic);
    }

    @Override
    public Collection<String> topics() {
        return List.copyOf(topics.keySet());
    }

    @Override
    public Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) {
        TopicChannel t = topics.get(topic);
        if (t == null) return java.util.Collections.emptyMap();
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(t.sessions));
    }

    @Override
    public int subscriberCount(String topic) {
        TopicChannel t = topics.get(topic);
        return t != null ? t.subscribers.get() : 0;
    }

    @Override
    public Map<String, Integer> topicSubscriberCounts() {
        Map<String, Integer> m = new LinkedHashMap<>();
        topics.forEach((id, t) -> m.put(id, t.subscribers.get()));
        return java.util.Collections.unmodifiableMap(m);
    }

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
