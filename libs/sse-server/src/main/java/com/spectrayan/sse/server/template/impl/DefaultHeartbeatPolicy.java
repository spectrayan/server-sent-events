package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.template.EventSerializer;
import com.spectrayan.sse.server.template.HeartbeatPolicy;
import com.spectrayan.sse.server.template.SseConnectContext;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Default heartbeat policy based on server properties.
 */
public class DefaultHeartbeatPolicy implements HeartbeatPolicy {
    private final SseServerProperties props;
    private final EventSerializer serializer;

    public DefaultHeartbeatPolicy(SseServerProperties props, EventSerializer serializer) {
        this.props = props;
        this.serializer = serializer;
    }

    @Override
    public Flux<ServerSentEvent<Object>> heartbeats(SseConnectContext ctx) {
        if (props == null || !props.getStream().isHeartbeatEnabled()) {
            return Flux.never();
        }
        Duration interval = props.getStream().getHeartbeatInterval();
        String event = props.getStream().getHeartbeatEventName();
        String data = props.getStream().getHeartbeatData();
        return Flux.interval(interval)
                .map(tick -> serializer.toSse(data, event, null));
    }
}
