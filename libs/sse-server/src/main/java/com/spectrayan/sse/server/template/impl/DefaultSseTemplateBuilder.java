package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.config.SseHeaderHandler;
import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.customize.SseEndpointCustomizer;
import com.spectrayan.sse.server.customize.SseHeaderCustomizer;
import com.spectrayan.sse.server.customize.SseStreamCustomizer;
import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.template.*;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Default builder constructing {@link com.spectrayan.sse.server.template.DefaultSseTemplate}.
 */
public class DefaultSseTemplateBuilder implements SseTemplateBuilder {
    private final SseEmitter emitter;
    private final SseHeaderHandler headerHandler;
    private final SseServerProperties props;
    private final ObjectProvider<SseStreamCustomizer> streamCustomizers;
    private final ObjectProvider<SseHeaderCustomizer> headerCustomizers;
    private final ObjectProvider<SseEndpointCustomizer> endpointCustomizers;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator;

    private EventSerializer serializer;
    private ClientFilter clientFilter;
    private ReconnectPolicy reconnectPolicy;
    private HeartbeatPolicy heartbeatPolicy;
    private ErrorMapper errorMapper;
    private ConnectionRegistry connectionRegistry;

    public DefaultSseTemplateBuilder(
            SseEmitter emitter,
            SseHeaderHandler headerHandler,
            SseServerProperties props,
            ObjectProvider<SseStreamCustomizer> streamCustomizers,
            ObjectProvider<SseHeaderCustomizer> headerCustomizers,
            ObjectProvider<SseEndpointCustomizer> endpointCustomizers,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator,
            EventSerializer serializer,
            ClientFilter clientFilter,
            ReconnectPolicy reconnectPolicy,
            HeartbeatPolicy heartbeatPolicy,
            ErrorMapper errorMapper,
            ConnectionRegistry connectionRegistry
    ) {
        this.emitter = emitter;
        this.headerHandler = headerHandler;
        this.props = props;
        this.streamCustomizers = streamCustomizers;
        this.headerCustomizers = headerCustomizers;
        this.endpointCustomizers = endpointCustomizers;
        this.eventPublisher = eventPublisher;
        this.sessionIdGenerator = sessionIdGenerator;
        this.serializer = serializer;
        this.clientFilter = clientFilter;
        this.reconnectPolicy = reconnectPolicy;
        this.heartbeatPolicy = heartbeatPolicy;
        this.errorMapper = errorMapper;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public SseTemplateBuilder serializer(EventSerializer serializer) { this.serializer = serializer; return this; }

    @Override
    public SseTemplateBuilder clientFilter(ClientFilter clientFilter) { this.clientFilter = clientFilter; return this; }

    @Override
    public SseTemplateBuilder reconnectPolicy(ReconnectPolicy reconnectPolicy) { this.reconnectPolicy = reconnectPolicy; return this; }

    @Override
    public SseTemplateBuilder heartbeatPolicy(HeartbeatPolicy heartbeatPolicy) { this.heartbeatPolicy = heartbeatPolicy; return this; }

    @Override
    public SseTemplateBuilder errorMapper(ErrorMapper errorMapper) { this.errorMapper = errorMapper; return this; }

    @Override
    public SseTemplateBuilder connectionRegistry(ConnectionRegistry connectionRegistry) { this.connectionRegistry = connectionRegistry; return this; }

    @Override
    public SseTemplate build() {
        return new com.spectrayan.sse.server.template.DefaultSseTemplate(
                emitter,
                headerHandler,
                props,
                streamCustomizers,
                headerCustomizers,
                endpointCustomizers,
                eventPublisher,
                sessionIdGenerator,
                serializer,
                clientFilter,
                reconnectPolicy,
                heartbeatPolicy,
                errorMapper,
                connectionRegistry
        );
    }
}
