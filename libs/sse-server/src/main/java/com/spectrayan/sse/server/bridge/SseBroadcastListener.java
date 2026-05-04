package com.spectrayan.sse.server.bridge;

/**
 * Callback interface for receiving broadcast SSE events from remote instances.
 * <p>
 * Registered with a {@link SseBroadcastBridge} via
 * {@link SseBroadcastBridge#subscribe(SseBroadcastListener)}. When an event
 * arrives from a remote instance, the bridge invokes
 * {@link #onRemoteEvent(SseBridgeMessage)} so that the local emitter can
 * inject it into the appropriate topic sink.
 *
 * @see SseBroadcastBridge
 * @see SseBridgeMessage
 * @since 2.0.0
 */
@FunctionalInterface
public interface SseBroadcastListener {

    /**
     * Called when an event from a remote instance is received.
     * <p>
     * The implementation should locate the local topic channel and inject
     * the event into its sink. If no local subscribers exist for the topic,
     * the event may be safely ignored.
     *
     * @param message the remote event envelope; never {@code null}
     */
    void onRemoteEvent(SseBridgeMessage message);
}
