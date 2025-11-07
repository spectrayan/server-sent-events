package com.spectrayan.sse.server.emitter;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Internal data holder for a single topic channel.
 * <p>
 * Contains:
 * - {@link reactor.core.publisher.Sinks.Many} for emitting {@link org.springframework.http.codec.ServerSentEvent} to subscribers.
 * - {@link java.util.concurrent.atomic.AtomicInteger} subscriber counter for max-limit enforcement and cleanup logic.
 * - Concurrent map of active {@link com.spectrayan.sse.server.session.SseSession} instances keyed by session id.
 * <p>
 * Package-private to keep the emitter surface minimal; managed by {@link TopicManager}.
 */
final class TopicChannel {
    final Sinks.Many<ServerSentEvent<Object>> sink;
    final AtomicInteger subscribers = new AtomicInteger(0);
    final ConcurrentHashMap<String, com.spectrayan.sse.server.session.SseSession> sessions = new ConcurrentHashMap<>();

    TopicChannel(Sinks.Many<ServerSentEvent<Object>> sink) {
        this.sink = sink;
    }
}
