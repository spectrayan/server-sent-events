/**
 * Bridge SPI for cross-instance SSE event fan-out.
 * <p>
 * In multi-pod/multi-instance deployments, SSE events emitted on one instance must
 * be delivered to clients connected to other instances. This package defines the
 * {@link com.spectrayan.sse.server.bridge.SseBroadcastBridge} SPI that decouples the
 * core emitter from the messaging infrastructure.
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@link com.spectrayan.sse.server.bridge.NoOpBroadcastBridge} — default for single-instance
 *       deployments (events stay local).</li>
 *   <li>{@code sse-server-bridge-cloud-stream} module — Spring Cloud Stream bridge that works
 *       with any binder (Kafka, RabbitMQ, Google Pub/Sub, Pulsar, Azure Event Hubs, etc.).</li>
 * </ul>
 *
 * @since 2.0.0
 */
package com.spectrayan.sse.server.bridge;
