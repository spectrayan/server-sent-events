package com.spectrayan.sse.server.template;

/**
 * Fluent builder for {@link SseTemplate} allowing customization of strategies and hooks.
 */
public interface SseTemplateBuilder {
    /**
     * Set the {@link EventSerializer} strategy used to convert payloads to SSE frames.
     *
     * @param serializer the serializer to use
     * @return this builder for chaining
     */
    SseTemplateBuilder serializer(EventSerializer serializer);

    /**
     * Set the {@link ClientFilter} used to allow/deny incoming connections.
     *
     * @param clientFilter filter invoked per connection
     * @return this builder for chaining
     */
    SseTemplateBuilder clientFilter(ClientFilter clientFilter);

    /**
     * Configure the {@link ReconnectPolicy} that advertises retry delays.
     *
     * @param reconnectPolicy policy that decides retry delay
     * @return this builder for chaining
     */
    SseTemplateBuilder reconnectPolicy(ReconnectPolicy reconnectPolicy);

    /**
     * Configure the {@link HeartbeatPolicy} for periodic heartbeat frames.
     *
     * @param heartbeatPolicy policy that produces heartbeats
     * @return this builder for chaining
     */
    SseTemplateBuilder heartbeatPolicy(HeartbeatPolicy heartbeatPolicy);

    /**
     * Set the {@link ErrorMapper} for mapping stream errors to SSE frames.
     *
     * @param errorMapper mapper that converts errors to events
     * @return this builder for chaining
     */
    SseTemplateBuilder errorMapper(ErrorMapper errorMapper);

    /**
     * Provide the {@link ConnectionRegistry} used for topic/session introspection.
     *
     * @param connectionRegistry registry implementation
     * @return this builder for chaining
     */
    SseTemplateBuilder connectionRegistry(ConnectionRegistry connectionRegistry);

    /**
     * Build a configured {@link SseTemplate} instance.
     *
     * @return the configured template
     */
    SseTemplate build();
}
