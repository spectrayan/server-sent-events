package com.spectrayan.sse.server.customize;

import com.spectrayan.sse.server.config.SseServerProperties;
import reactor.core.publisher.Sinks;

/**
 * Hook to customize the sink factory used by the emitter.
 * Implementations can replace or decorate the created {@link Sinks.Many} per topic.
 */
public interface SseEmitterCustomizer {
    <T> Sinks.Many<T> createSink(String topic, SseServerProperties properties);
}
