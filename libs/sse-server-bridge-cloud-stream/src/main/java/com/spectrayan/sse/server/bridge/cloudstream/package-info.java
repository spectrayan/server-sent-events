/**
 * Spring Cloud Stream bridge implementation for the Spectrayan SSE server library.
 * <p>
 * This module provides a {@link com.spectrayan.sse.server.bridge.SseBroadcastBridge}
 * implementation backed by Spring Cloud Stream's {@code StreamBridge} for publishing
 * and a functional {@code Consumer} bean for receiving.
 * <p>
 * The actual messaging broker is determined by which Spring Cloud Stream
 * <b>binder</b> the end user adds to their classpath:
 * <ul>
 *   <li>Kafka: {@code spring-cloud-stream-binder-kafka}</li>
 *   <li>RabbitMQ: {@code spring-cloud-stream-binder-rabbit}</li>
 *   <li>Google Cloud Pub/Sub: {@code spring-cloud-gcp-pubsub-stream-binder}</li>
 *   <li>Apache Pulsar: {@code spring-cloud-stream-binder-pulsar}</li>
 *   <li>Azure Event Hubs: {@code spring-cloud-azure-stream-binder-eventhubs}</li>
 * </ul>
 *
 * @since 2.0.0
 */
package com.spectrayan.sse.server.bridge.cloudstream;
