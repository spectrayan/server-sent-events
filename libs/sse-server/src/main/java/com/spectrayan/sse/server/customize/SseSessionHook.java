package com.spectrayan.sse.server.customize;

import com.spectrayan.sse.server.session.SseSession;
import reactor.core.publisher.SignalType;

/**
 * Hook to observe SSE session lifecycle events.
 */
public interface SseSessionHook {
    /** Called when a session joins (subscription starts). */
    default void onJoin(SseSession session) {}
    /** Called when a session leaves. */
    default void onLeave(SseSession session, SignalType signal) {}
}
