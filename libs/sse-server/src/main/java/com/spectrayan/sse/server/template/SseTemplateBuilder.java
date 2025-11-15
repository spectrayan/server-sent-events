package com.spectrayan.sse.server.template;

/**
 * Fluent builder for {@link SseTemplate} allowing customization of strategies and hooks.
 */
public interface SseTemplateBuilder {
    SseTemplateBuilder serializer(EventSerializer serializer);
    SseTemplateBuilder clientFilter(ClientFilter clientFilter);
    SseTemplateBuilder reconnectPolicy(ReconnectPolicy reconnectPolicy);
    SseTemplateBuilder heartbeatPolicy(HeartbeatPolicy heartbeatPolicy);
    SseTemplateBuilder errorMapper(ErrorMapper errorMapper);
    SseTemplateBuilder connectionRegistry(ConnectionRegistry connectionRegistry);
    SseTemplate build();
}
