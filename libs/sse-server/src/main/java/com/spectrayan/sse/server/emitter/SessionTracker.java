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

    SessionTracker(List<SseSessionHook> sessionHooks, TopicManager topicManager) {
        this.sessionHooks = sessionHooks != null ? sessionHooks : List.of();
        this.topicManager = topicManager;
    }

    Flux<ServerSentEvent<Object>> decorate(String topic, Flux<ServerSentEvent<Object>> upstream, TopicChannel channel, SseSession session) {
        return upstream
            .doOnSubscribe(sub -> {
                int count = channel.subscribers.incrementAndGet();
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
                boolean shouldCleanup = sig == SignalType.CANCEL || sig == SignalType.ON_ERROR;
                if (left <= 0 && shouldCleanup) {
                    channel.sink.tryEmitComplete();
                    topicManager.remove(topic);
                    log.info("SSE topic {} completed and removed (signal: {})", topic, sig);
                } else {
                    log.debug("Subscriber removed from topic {} (remaining: {}, signal: {})", topic, left, sig);
                }
            });
    }
}
