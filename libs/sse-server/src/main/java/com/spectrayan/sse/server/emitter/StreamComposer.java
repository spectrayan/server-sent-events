package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Composes the per-topic stream by merging the sink flux with a periodic heartbeat and an
 * optional initial "connected" event depending on configuration in {@link com.spectrayan.sse.server.config.SseServerProperties}.
 * <p>
 * Responsibilities:
 * - Heartbeat: emits {@code event=<heartbeatEventName>, data=<heartbeatData>} at the configured interval
 *   while the stream is active; stops when the sink completes.
 * - Connected event: prepends a single {@code event=<connectedEventName>, data=<connectedEventData>} item
 *   if enabled.
 * <p>
 * This class is package-private and focused solely on stream composition concerns.
 */
final class StreamComposer {

    private static final Logger log = LoggerFactory.getLogger(StreamComposer.class);

    private final SseServerProperties properties;

    StreamComposer(SseServerProperties properties) {
        this.properties = properties;
    }

    Flux<ServerSentEvent<Object>> compose(String topic, Flux<ServerSentEvent<Object>> sinkFlux) {
        Flux<ServerSentEvent<Object>> heartbeat = Flux.never();
        if (properties.getStream().isHeartbeatEnabled()) {
            heartbeat = Flux.interval(properties.getStream().getHeartbeatInterval())
                .map(t -> ServerSentEvent.<Object>builder(properties.getStream().getHeartbeatData())
                        .event(properties.getStream().getHeartbeatEventName())
                        .build())
                .doOnNext(ev -> log.trace("Sending heartbeat on topic {}", topic))
                .takeUntilOther(sinkFlux.ignoreElements());
        }

        Flux<ServerSentEvent<Object>> merged = Flux.merge(sinkFlux, heartbeat);
        if (properties.getStream().isConnectedEventEnabled()) {
            ServerSentEvent<Object> connected = ServerSentEvent.<Object>builder(properties.getStream().getConnectedEventData())
                .event(properties.getStream().getConnectedEventName())
                .build();
            merged = merged.startWith(connected);
        }
        return merged;
    }
}
