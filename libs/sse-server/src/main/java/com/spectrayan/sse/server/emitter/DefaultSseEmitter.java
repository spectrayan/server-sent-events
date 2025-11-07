package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.customize.SseEmitterCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link SseEmitter} backed by {@link AbstractSseEmitter} core logic.
 */
@Service
@ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DefaultSseEmitter extends AbstractSseEmitter implements SseEmitter {

    public DefaultSseEmitter(SseServerProperties properties,
                             ObjectProvider<SseEmitterCustomizer> sinkCustomizer,
                             ObjectProvider<com.spectrayan.sse.server.customize.SseSessionHook> sessionHooks,
                             com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator) {
        super(properties, sinkCustomizer, sessionHooks, sessionIdGenerator);
    }
}
