package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.customize.SseSessionHook;
import com.spectrayan.sse.server.session.SseSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.List;

/**
 * Handles subscription lifecycle bookkeeping (counters, session map, hooks, cleanup).
 */
final class SessionTracker {
    private static final Logger log = LoggerFactory.getLogger(SessionTracker.class);

    private final List<SseSessionHook> sessionHooks;
    private final TopicManager topicManager;
    private final com.spectrayan.sse.server.metrics.SseMetrics metrics;

    /**
     * Create a new {@code SessionTracker}.
     *
     * @param sessionHooks optional hooks invoked on join/leave; {@code null} treated as empty list
     * @param topicManager manager used to remove topics when the last subscriber leaves on cancel/error
     * @param metrics optional SSE metrics recorder; may be {@code null}
     */
    SessionTracker(List<SseSessionHook> sessionHooks, TopicManager topicManager, com.spectrayan.sse.server.metrics.SseMetrics metrics) {
        this.sessionHooks = sessionHooks != null ? sessionHooks : List.of();
        this.topicManager = topicManager;
        this.metrics = metrics;
    }

    /**
     * Decorate an upstream topic stream with subscription bookkeeping and cleanup.
     * <p>
     * Behavior:
     * - On subscribe: increment subscriber counter; if a {@link SseSession} is provided, store it in the
     *   channel's session map and invoke {@link SseSessionHook#onJoin(SseSession)} for each configured hook.
     * - On termination ({@link reactor.core.publisher.SignalType}): remove the session from the map (if present),
     *   invoke {@link SseSessionHook#onLeave(SseSession, reactor.core.publisher.SignalType)} on all hooks, decrement
     *   the subscriber counter, and when it reaches zero after a CANCEL or ON_ERROR, complete the sink and
     *   remove the topic via {@link TopicManager#remove(String)}.
     *
     * @param topic topic identifier (for logging and cleanup)
     * @param upstream the upstream flux to decorate
     * @param channel the per-topic channel state
     * @param session optional session for this subscriber; may be {@code null}
     * @return decorated flux with lifecycle side effects
     */
    Flux<ServerSentEvent<Object>> decorate(String topic, Flux<ServerSentEvent<Object>> upstream, TopicChannel channel, SseSession session) {
        return upstream
            .doOnSubscribe(sub -> {
                int count = channel.subscribers.incrementAndGet();
                if (metrics != null) metrics.recordConnection(topic);
                if (session != null) {
                    channel.sessions.put(session.getSessionId(), session);
                    for (var hook : sessionHooks) {
                        try { hook.onJoin(session); } catch (Throwable t) { log.debug("SseSessionHook.onJoin failed: {}", t.toString()); }
                    }
                }
                log.debug("Subscriber added to topic {} (now: {})", topic, count);
            })
            .doFinally(sig -> {
                if (session != null) {
                    channel.sessions.remove(session.getSessionId());
                    for (var hook : sessionHooks) {
                        try { hook.onLeave(session, sig); } catch (Throwable t) { log.debug("SseSessionHook.onLeave failed: {}", t.toString()); }
                    }
                }
                int left = channel.subscribers.decrementAndGet();
                if (metrics != null) metrics.recordDisconnection(topic);
                boolean shouldCleanup = sig == SignalType.CANCEL || sig == SignalType.ON_ERROR;
                // Use exact zero check to prevent double-cleanup when multiple
                // subscribers disconnect concurrently (TOCTOU race prevention).
                if (left == 0 && shouldCleanup) {
                    channel.sink.tryEmitComplete();
                    topicManager.remove(topic);
                    if (metrics != null) metrics.removeTopic(topic);
                    log.info("SSE topic {} completed and removed (signal: {})", topic, sig);
                } else {
                    log.debug("Subscriber removed from topic {} (remaining: {}, signal: {})", topic, left, sig);
                }
            });
    }
}
