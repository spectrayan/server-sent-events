package com.spectrayan.sse.server.template;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;

/**
 * High-level template for Server-Sent Events (SSE) that provides
 * convenience emission methods and an optional functional endpoint handler.
 *
 * This template builds upon the lower-level SseEmitter and existing
 * customizer hooks, hiding the event-sink/emitter implementation details.
 */
public interface SseTemplate {

    /**
     * Functional endpoint handler for SSE subscriptions. Use in a RouterFunction as:
     * {@code route().GET("/sse/{topic}", sseTemplate::handle)}.
     *
     * @param request the incoming server request
     * @return a {@link Mono} that writes the SSE response
     */
    Mono<ServerResponse> handle(ServerRequest request);

    /**
     * Core connection API that establishes a subscription to the given topic and returns
     * a composed Flux of SSE items according to configured policies (retry, heartbeat, error mapping, etc.).
     *
     * @param topic the topic id to connect to
     * @param context connection context including session id and headers
     * @return a hot {@link Flux} of {@link ServerSentEvent} items
     */
    Flux<ServerSentEvent<Object>> connect(String topic, SseConnectContext context);

    /**
     * Send a data-only SSE to a topic (alias of {@link #send(String, String, Object)} with null eventName).
     *
     * @param <T> payload type
     * @param topicId topic to send to
     * @param payload payload object to serialize as data
     */
    <T> void send(String topicId, T payload);

    /**
     * Send an SSE with an explicit event name to a topic.
     *
     * @param <T> payload type
     * @param topicId topic to send to
     * @param eventName event name (nullable)
     * @param payload payload object
     */
    <T> void send(String topicId, String eventName, T payload);

    /**
     * Send an SSE with event name and id to a topic.
     *
     * @param <T> payload type
     * @param topicId topic to send to
     * @param eventName event name (nullable)
     * @param payload payload object
     * @param id event id (nullable)
     */
    <T> void send(String topicId, String eventName, T payload, String id);

    /**
     * Broadcast a data-only SSE to all current topics.
     *
     * @param <T> payload type
     * @param payload payload to broadcast
     */
    <T> void broadcast(T payload);

    /**
     * Introspection over connected topics/sessions.
     *
     * @return the connection registry used by this template
     */
    ConnectionRegistry registry();
}
