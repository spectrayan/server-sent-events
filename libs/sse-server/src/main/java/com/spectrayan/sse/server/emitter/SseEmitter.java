package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.session.SseSession;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Collection;

/**
 * Public API for emitting SSE events and connecting subscribers.
 */
public interface SseEmitter {

    Flux<ServerSentEvent<Object>> connect(String topic);

    Flux<ServerSentEvent<Object>> connect(String topic, SseSession session);

    <T> void emitToTopic(String topicId, T payload);

    <T> void emitToTopic(String topicId, String eventName, T payload);

    <T> void emitToTopic(String topicId, String eventName, T payload, String id);

    <T> void emitToAll(T payload);

    Collection<String> currentTopics();

    <T> void emit(String topicId, T payload);

    <T> void emit(String topicId, String eventName, T payload);

    <T> void emit(String topicId, String eventName, T payload, String id);

    <T> void emit(T payload);

    void shutdown();
}
