package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.session.SseSession;
import com.spectrayan.sse.server.template.ConnectionRegistry;
import com.spectrayan.sse.server.topic.TopicRegistry;

import java.util.Collection;
import java.util.Map;

/**
 * Adapter that exposes {@link TopicRegistry} as a {@link ConnectionRegistry} for the template API.
 */
public class TopicConnectionRegistry implements ConnectionRegistry {
    private final TopicRegistry delegate;

    public TopicConnectionRegistry(TopicRegistry delegate) {
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
    public int count(String topic) {
        return delegate.subscriberCount(topic);
    }
}
