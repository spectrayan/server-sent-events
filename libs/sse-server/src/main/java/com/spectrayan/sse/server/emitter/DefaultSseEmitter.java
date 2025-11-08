package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.customize.SseEmitterCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default {@link SseEmitter} bean implementation that delegates to {@link AbstractSseEmitter}
 * for the core orchestration logic (topic management, validation, composition, lifecycle events).
 * <p>
 * Bean lifecycle:
 * - Registered as a Spring {@link org.springframework.stereotype.Service} when
 *   {@code spectrayan.sse.server.enabled=true} (default) via {@link ConditionalOnProperty}.
 * - Suitable for most applications; customization points are provided via DI of
 *   {@link com.spectrayan.sse.server.customize.SseEmitterCustomizer} and session hooks.
 */
@Service
@ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DefaultSseEmitter extends AbstractSseEmitter implements SseEmitter {

    /**
     * Create the default emitter delegating to {@link AbstractSseEmitter}.
     *
     * @param properties server properties controlling sink type, replay size, heartbeat/connected events
     * @param sinkCustomizer optional customizer allowing alternative sink creation strategy
     * @param sessionHooks optional hooks invoked on session join/leave
     * @param sessionIdGenerator optional generator used by components needing to derive a session id
     */
    public DefaultSseEmitter(SseServerProperties properties,
                             ObjectProvider<SseEmitterCustomizer> sinkCustomizer,
                             ObjectProvider<com.spectrayan.sse.server.customize.SseSessionHook> sessionHooks,
                             com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator) {
        super(properties, sinkCustomizer, sessionHooks, sessionIdGenerator);
    }
}
