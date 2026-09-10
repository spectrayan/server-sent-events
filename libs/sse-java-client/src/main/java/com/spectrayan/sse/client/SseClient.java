package com.spectrayan.sse.client;

import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Flux;

import java.util.stream.Stream;

/**
 * Non-blocking, reactive client for consuming Server-Sent Events (SSE) streams.
 */
public interface SseClient {

    /**
     * Streams events mapped directly to the specified target type.
     */
    <T> Flux<T> stream(Class<T> targetClass);

    /**
     * Streams events matching a specific event name, mapped to the target type.
     */
    <T> Flux<T> stream(String eventType, Class<T> targetClass);

    /**
     * Streams events mapped using a ParameterizedTypeReference (for generic collections).
     */
    <T> Flux<T> stream(ParameterizedTypeReference<T> typeRef);

    /**
     * Streams events matching a specific event name, mapped using ParameterizedTypeReference.
     */
    <T> Flux<T> stream(String eventType, ParameterizedTypeReference<T> typeRef);

    /**
     * Streams full SseEvent metadata wrappers (including id, event name, retry).
     */
    <T> Flux<SseEvent<T>> streamEvents(Class<T> targetClass);

    /**
     * Streams full SseEvent metadata wrappers matching a specific event name.
     */
    <T> Flux<SseEvent<T>> streamEvents(String eventType, Class<T> targetClass);

    /**
     * Streams full SseEvent metadata wrappers using a ParameterizedTypeReference.
     */
    <T> Flux<SseEvent<T>> streamEvents(ParameterizedTypeReference<T> typeRef);

    /**
     * Streams full SseEvent metadata wrappers matching a specific event name using ParameterizedTypeReference.
     */
    <T> Flux<SseEvent<T>> streamEvents(String eventType, ParameterizedTypeReference<T> typeRef);

    /**
     * Blocking iterator/stream adapter suitable for Java 21 Virtual Threads (Loom).
     */
    <T> Stream<T> streamBlocking(Class<T> targetClass);

    /**
     * Blocking iterator/stream adapter filtered by event name for Java 21 Virtual Threads.
     */
    <T> Stream<T> streamBlocking(String eventType, Class<T> targetClass);

    static DefaultSseClient.Builder builder() {
        return DefaultSseClient.builder();
    }
}