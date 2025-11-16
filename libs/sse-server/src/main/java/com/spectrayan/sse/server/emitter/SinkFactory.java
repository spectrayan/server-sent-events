package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.customize.SseEmitterCustomizer;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

/**
 * Factory responsible for creating per-topic {@link reactor.core.publisher.Sinks.Many} instances
 * for SSE delivery based on {@link com.spectrayan.sse.server.config.SseServerProperties} and an
 * optional {@link com.spectrayan.sse.server.customize.SseEmitterCustomizer}.
 * <p>
 * Behavior:
 * - If a customizer is provided and returns a non-null sink from {@code createSink}, it is used.
 * - Otherwise a sink is created according to {@code spectrayan.sse.server.emitter.sink-type}:
 *   - REPLAY: uses Reactor replay sink; size is limited by {@code replay-size} (all if 0).
 *   - MULTICAST: uses {@code Sinks.many().multicast().directBestEffort()} suitable for hot streams.
 */
final class SinkFactory {

    private final SseServerProperties properties;
    private final SseEmitterCustomizer sinkCustomizer;

    /**
     * Create a new {@code SinkFactory}.
     *
     * @param properties server properties influencing sink type and replay size
     * @param sinkCustomizer optional customizer that may override sink creation; may be {@code null}
     */
    SinkFactory(SseServerProperties properties, SseEmitterCustomizer sinkCustomizer) {
        this.properties = properties;
        this.sinkCustomizer = sinkCustomizer;
    }

    /**
     * Create a per-topic {@link Sinks.Many} sink according to configuration and optional customizer.
     * <p>
     * Resolution order:
     * 1) If a {@link SseEmitterCustomizer} is provided and returns a non-null sink from
     *    {@code createSink(topic, properties)}, that sink is used as-is.
     * 2) Otherwise the sink type is chosen by {@code spectrayan.sse.server.emitter.sink-type}:
     *    - {@code REPLAY}: a replay sink is created; if {@code replay-size} > 0, it is limited to that size,
     *      otherwise it replays all.
     *    - {@code MULTICAST}: {@code Sinks.many().multicast().directBestEffort()} is used for hot streams.
     * <p>
     * Note: a type cast with {@code @SuppressWarnings("unchecked")} is applied to accommodate
     * the generic signature used by {@link SseEmitterCustomizer}.
     *
     * @param topic topic identifier for which the sink is created
     * @return the created sink
     */
    Sinks.Many<ServerSentEvent<Object>> create(String topic) {
        if (sinkCustomizer != null) {
            @SuppressWarnings("unchecked")
            Sinks.Many<ServerSentEvent<Object>> custom = (Sinks.Many<ServerSentEvent<Object>>) (Sinks.Many<?>) sinkCustomizer.createSink(topic, properties);
            if (custom != null) return custom;
        }
        SseServerProperties.SinkType type = properties.getEmitter().getSinkType();
        return switch (type) {
            case REPLAY -> {
                int size = Math.max(0, properties.getEmitter().getReplaySize());
                if (size > 0) {
                    yield Sinks.many().replay().limit(size);
                } else {
                    yield Sinks.many().replay().all();
                }
            }
            case MULTICAST -> Sinks.many().multicast().directBestEffort();
        };
    }
}
