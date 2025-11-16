package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.template.ReconnectPolicy;
import com.spectrayan.sse.server.template.SseConnectContext;

import java.util.Optional;

/**
 * Default reconnect policy based on {@link SseServerProperties.Stream} settings.
 */
public class DefaultReconnectPolicy implements ReconnectPolicy {
    private final SseServerProperties props;

    public DefaultReconnectPolicy(SseServerProperties props) {
        this.props = props;
    }

    @Override
    public Optional<Long> retryDelayMillis(SseConnectContext ctx) {
        if (props != null && props.getStream().isRetryEnabled() && props.getStream().getRetry() != null) {
            return Optional.of(props.getStream().getRetry().toMillis());
        }
        return Optional.empty();
    }
}
