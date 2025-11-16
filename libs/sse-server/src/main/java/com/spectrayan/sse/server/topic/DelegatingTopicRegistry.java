package com.spectrayan.sse.server.topic;

import com.spectrayan.sse.server.session.SseSession;

import java.util.Collection;
import java.util.Map;

/**
 * Wrapper that exposes only the {@link TopicRegistry} view while delegating to another
 * {@link TopicRegistry} instance (typically an {@code SseEmitter} that also implements it).
 *
 * Using this adapter allows Spring's bean type resolution to distinguish between the
 * emitter bean and the registry bean, avoiding multiple beans of {@code SseEmitter} type
 * when the same instance implements both interfaces.
 */
public final class DelegatingTopicRegistry implements TopicRegistry {
    private final TopicRegistry delegate;

    public DelegatingTopicRegistry(TopicRegistry delegate) {
        this.delegate = delegate;
    }

    @Override
    public Collection<String> topics() {
        return delegate.topics();
    }

    @Override
    public Map<String, SseSession> sessions(String topic) {
        return delegate.sessions(topic);
    }

    @Override
    public int subscriberCount(String topic) {
        return delegate.subscriberCount(topic);
    }

    @Override
    public Map<String, Integer> topicSubscriberCounts() {
        return delegate.topicSubscriberCounts();
    }
}
