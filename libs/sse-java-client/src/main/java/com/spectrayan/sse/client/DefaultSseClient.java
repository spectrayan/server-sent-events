package com.spectrayan.sse.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Default Spring WebClient-backed implementation of SseClient.
 */
public class DefaultSseClient implements SseClient {

    private final WebClient webClient;
    private final String uri;
    private final SseReconnectionConfig reconnectionConfig;
    private final ObjectMapper objectMapper;
    private final Consumer<HttpHeaders> defaultHeaders;

    private DefaultSseClient(Builder builder) {
        this.uri = builder.uri != null ? builder.uri : "";
        this.reconnectionConfig = builder.reconnectionConfig != null
            ? builder.reconnectionConfig
            : SseReconnectionConfig.defaultConfiguration();
        this.objectMapper = builder.objectMapper != null
            ? builder.objectMapper
            : new ObjectMapper();
        this.defaultHeaders = builder.defaultHeaders;

        if (builder.webClient != null) {
            this.webClient = builder.webClient;
        } else if (builder.baseUrl != null && !builder.baseUrl.isBlank()) {
            this.webClient = WebClient.builder().baseUrl(builder.baseUrl).build();
        } else {
            this.webClient = WebClient.builder().build();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <T> Flux<T> stream(Class<T> targetClass) {
        return stream(null, targetClass);
    }

    @Override
    public <T> Flux<T> stream(String eventType, Class<T> targetClass) {
        return streamEvents(eventType, targetClass).map(SseEvent::data);
    }

    @Override
    public <T> Flux<T> stream(ParameterizedTypeReference<T> typeRef) {
        return stream(null, typeRef);
    }

    @Override
    public <T> Flux<T> stream(String eventType, ParameterizedTypeReference<T> typeRef) {
        return streamEvents(eventType, typeRef).map(SseEvent::data);
    }

    @Override
    public <T> Flux<SseEvent<T>> streamEvents(Class<T> targetClass) {
        return streamEvents(null, targetClass);
    }

    @Override
    public <T> Flux<SseEvent<T>> streamEvents(String eventType, Class<T> targetClass) {
        JavaType javaType = objectMapper.constructType(targetClass);
        return createStream(eventType, javaType);
    }

    @Override
    public <T> Flux<SseEvent<T>> streamEvents(ParameterizedTypeReference<T> typeRef) {
        return streamEvents(null, typeRef);
    }

    @Override
    public <T> Flux<SseEvent<T>> streamEvents(String eventType, ParameterizedTypeReference<T> typeRef) {
        JavaType javaType = objectMapper.constructType(typeRef.getType());
        return createStream(eventType, javaType);
    }

    @Override
    public <T> Stream<T> streamBlocking(Class<T> targetClass) {
        return stream(targetClass).toStream();
    }

    @Override
    public <T> Stream<T> streamBlocking(String eventType, Class<T> targetClass) {
        return stream(eventType, targetClass).toStream();
    }

    private <T> Flux<SseEvent<T>> createStream(String eventType, JavaType javaType) {
        AtomicReference<String> lastEventId = new AtomicReference<>();

        Flux<SseEvent<T>> rawFlux = Flux.defer(() -> {
            WebClient.RequestHeadersSpec<?> spec = webClient.get()
                .uri(uri)
                .accept(MediaType.TEXT_EVENT_STREAM);

            if (defaultHeaders != null) {
                spec = spec.headers(defaultHeaders);
            }

            String lastId = lastEventId.get();
            if (lastId != null && !lastId.isBlank()) {
                spec = spec.header("Last-Event-ID", lastId);
            }

            Flux<ServerSentEvent<String>> sseFlux = spec.retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .doOnNext(sse -> {
                    if (sse.id() != null && !sse.id().isBlank()) {
                        lastEventId.set(sse.id());
                    }
                });

            if (eventType != null && !eventType.isBlank()) {
                sseFlux = sseFlux.filter(sse -> eventType.equals(sse.event()));
            }

            return sseFlux
                .filter(sse -> sse.data() != null && !sse.data().isBlank())
                .map(sse -> {
                    T deserialized = deserialize(sse.data(), javaType);
                    return SseEvent.of(sse.id(), sse.event(), deserialized, sse.retry(), sse.data());
                });
        });

        if (reconnectionConfig.enabled()) {
            rawFlux = rawFlux
                .retryWhen(BackoffStrategy.createRetry(reconnectionConfig))
                .repeatWhen(companion -> companion.flatMap(v -> {
                    Duration delay = BackoffStrategy.computeDelay(0, reconnectionConfig);
                    return Mono.delay(delay);
                }));
        }

        return rawFlux;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserialize(String rawData, JavaType javaType) {
        if (javaType.getRawClass().equals(String.class)) {
            return (T) rawData;
        }
        try {
            return objectMapper.readValue(rawData, javaType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize SSE payload: " + rawData, e);
        }
    }

    public static class Builder {
        private String baseUrl;
        private String uri = "";
        private WebClient webClient;
        private SseReconnectionConfig reconnectionConfig;
        private ObjectMapper objectMapper;
        private Consumer<HttpHeaders> defaultHeaders;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder webClient(WebClient webClient) {
            this.webClient = webClient;
            return this;
        }

        public Builder reconnection(SseReconnectionConfig reconnectionConfig) {
            this.reconnectionConfig = reconnectionConfig;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder defaultHeaders(Consumer<HttpHeaders> defaultHeaders) {
            this.defaultHeaders = defaultHeaders;
            return this;
        }

        public SseClient build() {
            return new DefaultSseClient(this);
        }
    }
}