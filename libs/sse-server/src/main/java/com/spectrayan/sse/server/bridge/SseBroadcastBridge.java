package com.spectrayan.sse.server.bridge;

/**
 * SPI for cross-instance SSE event fan-out.
 * <p>
 * When deployed on multiple pods/instances behind a load balancer,
 * events emitted on one instance must be delivered to SSE clients
 * connected to other instances. Implementations bridge local
 * {@link com.spectrayan.sse.server.emitter.SseEmitter#emit} calls
 * to a shared messaging infrastructure (Redis Pub/Sub, Kafka,
 * RabbitMQ, Google Cloud Pub/Sub, etc.).
 * <p>
 * The library provides a {@link NoOpBroadcastBridge no-op default} for
 * single-instance deployments. For multi-instance support, add the
 * {@code sse-server-bridge-cloud-stream} module plus a Spring Cloud Stream
 * binder dependency for your chosen broker.
 * <p>
 * Custom implementations may also be registered as Spring beans; the first
 * {@code SseBroadcastBridge} bean found will replace the no-op default.
 *
 * @see SseBridgeMessage
 * @see SseBroadcastListener
 * @see NoOpBroadcastBridge
 * @since 2.0.0
 */
public interface SseBroadcastBridge {

    /**
     * Publish an SSE event so that all instances (including this one) can
     * deliver it to their locally connected subscribers.
     * <p>
     * Called by the emission service after the event has been emitted to the
     * local sink. Implementations should serialize and send the message to the
     * shared channel/topic. The originating instance id is included in the
     * {@link SseBridgeMessage} so receivers can skip re-injection of their
     * own events.
     * <p>
     * This method is invoked on the emitting thread. Implementations that
     * perform I/O should consider asynchronous dispatch to avoid blocking
     * the caller.
     *
     * @param message the event envelope to broadcast; never {@code null}
     */
    void publish(SseBridgeMessage message);

    /**
     * Register a listener that receives events published by remote instances.
     * <p>
     * Called once during emitter initialization. The listener callback
     * ({@link SseBroadcastListener#onRemoteEvent}) should inject the received
     * event into the local topic sink so that locally connected SSE clients
     * receive it.
     *
     * @param listener callback invoked for each remote event; never {@code null}
     */
    void subscribe(SseBroadcastListener listener);

    /**
     * Lifecycle hook for cleanup (unsubscribe from channels, close connections, etc.).
     * <p>
     * Called on application shutdown. The default implementation is a no-op.
     */
    default void close() {}
}
