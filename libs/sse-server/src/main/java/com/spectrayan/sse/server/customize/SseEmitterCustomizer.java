package com.spectrayan.sse.server.customize;

import com.spectrayan.sse.server.config.SseServerProperties;
import reactor.core.publisher.Sinks;

/**
 * Hook to customize the sink factory used by the emitter.
 * Implementations can replace or decorate the created {@link Sinks.Many} per topic.
 */
public interface SseEmitterCustomizer {
    /**
     * Create a new sink for the given topic.
     *
     * @param <T> element type held in the sink
     * @param topic the topic identifier
     * @param properties server properties that may influence backpressure/buffering
     * @return a new {@link Sinks.Many} instance
     */
    <T> Sinks.Many<T> createSink(String topic, SseServerProperties properties);
}
