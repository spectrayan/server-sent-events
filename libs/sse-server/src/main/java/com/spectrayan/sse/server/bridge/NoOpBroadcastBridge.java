package com.spectrayan.sse.server.bridge;

/**
 * Default no-op implementation of {@link SseBroadcastBridge} for single-instance
 * deployments.
 * <p>
 * {@link #publish(SseBridgeMessage)} is a silent no-op: events are only delivered
 * to the local Reactor sink (the pre-2.0 behavior). {@link #subscribe} accepts
 * but ignores the listener since there are no remote events to deliver.
 * <p>
 * This bean is auto-registered by {@code SseServerAutoConfiguration} when no
 * other {@code SseBroadcastBridge} bean is present on the classpath.
 *
 * @since 2.0.0
 */
public final class NoOpBroadcastBridge implements SseBroadcastBridge {

    @Override
    public void publish(SseBridgeMessage message) {
        // No-op: single instance, no cross-pod fan-out needed
    }

    @Override
    public void subscribe(SseBroadcastListener listener) {
        // No-op: no remote events to receive
    }
}
