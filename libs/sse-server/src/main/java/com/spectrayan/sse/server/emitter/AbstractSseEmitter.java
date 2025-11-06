package com.spectrayan.sse.server.emitter;

import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.error.EmissionRejectedException;
import com.spectrayan.sse.server.error.InvalidTopicException;
import com.spectrayan.sse.server.error.TopicNotFoundException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SignalType;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-Sent Events (SSE) topic emitter.
 * <p>
 * This service manages a set of per-topic reactive sinks (one sink per topic) and exposes
 * a reactive stream (`Flux`) for clients to subscribe to via {@link #connect(String)}.
 * Producers can push arbitrary/complex payload objects to specific topics (or broadcast to all)
 * using the various {@code emit*} methods. The emitter takes care of building
 * {@link ServerSentEvent} instances so callers can send any object type without dealing
 * with SSE formatting in controllers.
 * <p>
 * Key behaviors:
 * - Topics are created lazily the first time a client connects or an event is emitted for them.
 * - A lightweight heartbeat event with {@code event="heartbeat"} and data {@code "::heartbeat::"}
 *   is merged into each topic stream every 15 seconds while the stream is active, helping proxies
 *   and clients keep the connection alive.
 * - Topics remain active in memory even if the last subscriber disconnects. The server keeps
 *   connections/topics alive and will only complete and remove a topic when a client cancels the
 *   subscription, an error occurs, or the application terminates (graceful shutdown).
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AbstractSseEmitter {

    private static final Logger log = LoggerFactory.getLogger(AbstractSseEmitter.class);

    private final SseServerProperties properties;
    private final com.spectrayan.sse.server.customize.SseEmitterCustomizer sinkCustomizer;

    public AbstractSseEmitter(SseServerProperties properties, org.springframework.beans.factory.ObjectProvider<com.spectrayan.sse.server.customize.SseEmitterCustomizer> sinkCustomizer) {
        this.properties = properties;
        this.sinkCustomizer = sinkCustomizer != null ? sinkCustomizer.getIfAvailable() : null;
    }

    private Sinks.Many<ServerSentEvent<Object>> createSink(String topic) {
        if (sinkCustomizer != null) {
            @SuppressWarnings("unchecked")
            Sinks.Many<ServerSentEvent<Object>> custom = (Sinks.Many<ServerSentEvent<Object>>) (Sinks.Many<?>) sinkCustomizer.createSink(topic, properties);
            if (custom != null) return custom;
        }
        SseServerProperties.SinkType type = properties.getEmitter().getSinkType();
        return switch (type) {
            case REPLAY -> {
                int size = Math.max(0, properties.getEmitter().getReplaySize());
                if (size > 0) {
                    yield Sinks.many().replay().limit(size);
                } else {
                    yield Sinks.many().replay().all();
                }
            }
            case MULTICAST -> Sinks.many().multicast().directBestEffort();
        };
    }

    /**
     * Holder for the per-topic sink and its live subscriber count.
     */
    private static class SseTopic {
        final Sinks.Many<ServerSentEvent<Object>> sink;
        final AtomicInteger subscribers = new AtomicInteger(0);
        SseTopic(Sinks.Many<ServerSentEvent<Object>> sink) { this.sink = sink; }
    }

    private final Map<String, SseTopic> topics = new ConcurrentHashMap<>();

    /**
     * Connect to a topic and receive a live stream of {@link ServerSentEvent} items.
     * <p>
     * The topic channel is created on first access. A periodic heartbeat event is merged into the
     * stream (every ~15s) to keep connections alive. When the last subscriber disconnects, the topic
     * sink is completed and removed.
     *
     * @param topic the topic identifier (path segment used by clients to subscribe)
     * @return a hot {@link Flux} of {@link ServerSentEvent} carrying objects previously emitted to the topic
     */
    public Flux<ServerSentEvent<Object>> connect(String topic) {
        validateTopicOrThrow(topic);
        SseTopic channel = topics.computeIfAbsent(topic, id -> {
            log.info("Creating SSE topic: {}", id);
            return new SseTopic(createSink(id));
        });

        // Enforce max subscribers if configured
        int max = properties.getTopics().getMaxSubscribers();
        return Flux.defer(() -> {
            if (max > 0 && channel.subscribers.get() >= max) {
                throw new com.spectrayan.sse.server.error.SseException(
                        com.spectrayan.sse.server.error.ErrorCode.SUBSCRIPTION_REJECTED,
                        "Max subscribers exceeded for topic " + topic,
                        topic
                );
            }

            Flux<ServerSentEvent<Object>> sinkFlux = channel.sink.asFlux();

            Flux<ServerSentEvent<Object>> heartbeat = Flux.never();
            if (properties.getStream().isHeartbeatEnabled()) {
                heartbeat = Flux.interval(properties.getStream().getHeartbeatInterval())
                    .map(t -> ServerSentEvent.<Object>builder(properties.getStream().getHeartbeatData())
                            .event(properties.getStream().getHeartbeatEventName())
                            .build())
                    .doOnNext(ev -> log.trace("Sending heartbeat on topic {}", topic))
                    .takeUntilOther(sinkFlux.ignoreElements());
            }

            Flux<ServerSentEvent<Object>> merged = Flux.merge(sinkFlux, heartbeat);
            if (properties.getStream().isConnectedEventEnabled()) {
                ServerSentEvent<Object> connected = ServerSentEvent.<Object>builder(properties.getStream().getConnectedEventData())
                    .event(properties.getStream().getConnectedEventName())
                    .build();
                merged = merged.startWith(connected);
            }

            return merged
                    .doOnSubscribe(sub -> {
                        int count = channel.subscribers.incrementAndGet();
                        log.debug("Subscriber added to topic {} (now: {})", topic, count);
                    })
                    .doFinally(sig -> {
                        int left = channel.subscribers.decrementAndGet();
                        boolean shouldCleanup = sig == SignalType.CANCEL || sig == SignalType.ON_ERROR;
                        if (left <= 0 && shouldCleanup) {
                            channel.sink.tryEmitComplete();
                            topics.remove(topic);
                            log.info("SSE topic {} completed and removed (signal: {})", topic, sig);
                        } else {
                            log.debug("Subscriber removed from topic {} (remaining: {}, signal: {})", topic, left, sig);
                        }
                    });
        });
    }

    /**
     * Emit a payload to a specific topic.
     * <p>
     * If the topic does not exist or has no active subscribers, the payload is ignored.
     * The payload can be any serializable object; it will be wrapped into a {@link ServerSentEvent}
     * without an explicit event name or id.
     *
     * @param topicId the target topic id
     * @param payload the payload object to send (any type supported by your HTTP message converters)
     * @param <T>     the payload type
     */
    public <T> void emitToTopic(String topicId, T payload) {
        emitToTopic(topicId, null, payload, null);
    }

    /**
     * Emit a payload with a custom SSE event name to a specific topic.
     *
     * @param topicId   the target topic id
     * @param eventName the SSE event name to set (e.g. "orderCreated"); may be {@code null}
     * @param payload   the payload object to send
     * @param <T>       the payload type
     */
    public <T> void emitToTopic(String topicId, String eventName, T payload) {
        emitToTopic(topicId, eventName, payload, null);
    }

    /**
     * Emit a payload with a custom SSE event name and id to a specific topic.
     * <p>
     * If the topic channel does not exist, nothing happens.
     *
     * @param topicId   the target topic id
     * @param eventName the SSE event name to set; may be {@code null}
     * @param payload   the payload object to send
     * @param id        optional SSE id (used by clients for reconnection via Last-Event-ID); may be {@code null}
     * @param <T>       the payload type
     */
    public <T> void emitToTopic(String topicId, String eventName, T payload, String id) {
        validateTopicOrThrow(topicId);
        SseTopic channel = topics.get(topicId);
        if (channel == null) {
            throw new TopicNotFoundException(topicId);
        }
        ServerSentEvent.Builder<Object> builder = ServerSentEvent.<Object>builder((Object) payload);
        if (eventName != null) builder.event(eventName);
        if (id != null) builder.id(id);
        if(log.isDebugEnabled()) {
            log.debug("Emitting to topic {} eventName={} id={} payload={}", topicId, eventName, id, describePayload(payload));
        }
        Sinks.EmitResult result = channel.sink.tryEmitNext(builder.build());
        if (result.isFailure()) {
            throw mapEmitFailure(topicId, result, eventName, id);
        }
    }

    private String describePayload(Object payload) {
        if (payload == null) return "null";
        String type = payload.getClass().getSimpleName();
        int hash = System.identityHashCode(payload);
        return switch (payload) {
            case CharSequence cs -> type + "[len=" + cs.length() + "]#" + hash;
            case byte[] bytes -> type + "[len=" + bytes.length + "]#" + hash;
            case Collection<?> col -> type + "[size=" + col.size() + "]#" + hash;
            case Map<?, ?> map -> type + "[size=" + map.size() + "]#" + hash;
            default -> type + "#" + hash;
        };
    }

    private void validateTopicOrThrow(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new InvalidTopicException(topic, "Topic must not be null or blank");
        }
        String pattern = properties.getTopics().getPattern();
        if (pattern != null && !topic.matches(pattern)) {
            throw new InvalidTopicException(topic, "Topic contains illegal characters; allowed pattern: " + pattern);
        }
    }

    private EmissionRejectedException mapEmitFailure(String topic, Sinks.EmitResult result, String eventName, String id) {
        String safeEventName = eventName != null ? eventName : "";
        String safeId = id != null ? id : "";
        String emitResultName = (result != null) ? result.name() : "NULL";
        log.warn("Failed to emit to topic {} result={} eventName={} id={}", topic, emitResultName, safeEventName, safeId);
        return new EmissionRejectedException(topic, emitResultName, Map.of(
                "emitResult", emitResultName,
                "eventName", safeEventName,
                "id", safeId
        ));
    }

    /**
     * Broadcast a payload to all currently active topics.
     *
     * @param payload the payload object to send to every topic
     * @param <T>     the payload type
     */
    public <T> void emitToAll(T payload) {
        int count = topics.size();
        if (count == 0) {
            log.warn("No active topics to broadcast to; payload ignored");
            return;
            //throw new NoSubscribersException("No active topics/subscribers to broadcast to");
        }
        ServerSentEvent<Object> event = ServerSentEvent.<Object>builder((Object) payload).build();
        if(log.isDebugEnabled()) {
            log.debug("Broadcasting to {} topic(s) payload={}", count, describePayload(payload));
        }
        topics.forEach((id, channel) -> {
            Sinks.EmitResult res = channel.sink.tryEmitNext(event);
            if (res.isFailure()) {
                log.warn("Broadcast emit rejected for topic {} result={}", id, res);
            }
        });
    }

    /**
     * Get the identifiers of topics that currently have an active channel in memory.
     * <p>
     * Note: this is a snapshot view of the backing map keys and may change immediately after return.
     *
     * @return a collection of active topic ids
     */
    public Collection<String> currentTopics() {
        return topics.keySet();
    }

    /**
     * Convenience alias: emit a payload to a topic.
     * <p>
     * Equivalent to {@link #emitToTopic(String, Object)}.
     *
     * @param topicId the target topic id
     * @param payload the payload object to send
     * @param <T>     the payload type
     */
    public <T> void emit(String topicId, T payload) {
        emitToTopic(topicId, payload);
    }

    /**
     * Convenience alias: emit a payload with event name to a topic.
     * <p>
     * Equivalent to {@link #emitToTopic(String, String, Object)}.
     *
     * @param topicId   the target topic id
     * @param eventName the SSE event name
     * @param payload   the payload object to send
     * @param <T>       the payload type
     */
    public <T> void emit(String topicId, String eventName, T payload) {
        emitToTopic(topicId, eventName, payload);
    }

    /**
     * Convenience alias: emit a payload with event name and id to a topic.
     * <p>
     * Equivalent to {@link #emitToTopic(String, String, Object, String)}.
     *
     * @param topicId   the target topic id
     * @param eventName the SSE event name
     * @param payload   the payload object to send
     * @param id        optional SSE id
     * @param <T>       the payload type
     */
    public <T> void emit(String topicId, String eventName, T payload, String id) {
        emitToTopic(topicId, eventName, payload, id);
    }

    /**
     * Broadcast alias: emit to all connected topics.
     * <p>
     * Equivalent to {@link #emitToAll(Object)}.
     *
     * @param payload the payload object to broadcast to all topics
     * @param <T>     the payload type
     */
    public <T> void emit(T payload) {
        emitToAll(payload);
    }

    /**
     * Gracefully close all active SSE channels on application shutdown to avoid
     * blocking graceful shutdown with in-flight requests.
     * <p>
     * This completes each sink and clears the topic registry. Any subsequent attempt to connect
     * will recreate topics on demand.
     */
    @PreDestroy
    public void shutdown() {
        int count = topics.size();
        if (count > 0) {
            log.info("Shutting down AbstractSseEmitter: completing {} SSE channel(s)", count);
        } else {
            log.info("Shutting down AbstractSseEmitter: no active SSE channels");
        }
        topics.forEach((id, ch) -> {
            try {
                ch.sink.tryEmitComplete();
            } catch (Throwable t) {
                log.warn("Error completing SSE channel for topic {}: {}", id, t.getMessage());
            }
        });
        topics.clear();
    }
}
