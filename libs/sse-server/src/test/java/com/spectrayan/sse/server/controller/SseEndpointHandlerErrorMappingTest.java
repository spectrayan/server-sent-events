package com.spectrayan.sse.server.controller;

import com.spectrayan.sse.server.config.SseServerAutoConfiguration;
import com.spectrayan.sse.server.emitter.SseEmitter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.codec.ServerSentEvent;

import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that when the core emitter errors immediately, the endpoint maps errors to SSE when enabled.
 */
class SseEndpointHandlerErrorMappingTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class, TestConfig.class))
                .withPropertyValues(
                        "spectrayan.sse.server.enabled=true",
                        "spectrayan.sse.server.base-path=/sse",
                        "spectrayan.sse.server.stream.retry-enabled=false",
                        "spectrayan.sse.server.stream.map-errors-to-sse=true"
                );
    }

    @Test
    void firstEventIsMappedErrorWhenEmitterErrors() {
        runner().run(ctx -> {
            assertThat(ctx).hasBean("sseRouterFunction");
            @SuppressWarnings("unchecked")
            org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> router =
                    (org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse>) ctx.getBean("sseRouterFunction");
            org.springframework.test.web.reactive.server.WebTestClient client =
                    org.springframework.test.web.reactive.server.WebTestClient.bindToRouterFunction(router).build();

            var body = client.get().uri("/sse/boom").exchange()
                    .expectStatus().isOk()
                    .returnResult(ServerSentEvent.class)
                    .getResponseBody();

            ServerSentEvent<?> evt = body.blockFirst(Duration.ofSeconds(2));
            assertThat(evt).isNotNull();
            assertThat(evt.event()).isEqualTo("error");
        });
    }

    @Configuration
    static class TestConfig {
        static class TestEmitter implements SseEmitter, com.spectrayan.sse.server.topic.TopicRegistry {
            @Override public reactor.core.publisher.Flux<ServerSentEvent<Object>> connect(String topic) { return reactor.core.publisher.Flux.error(new RuntimeException("boom")); }
            @Override public reactor.core.publisher.Flux<ServerSentEvent<Object>> connect(String topic, com.spectrayan.sse.server.session.SseSession session) { return reactor.core.publisher.Flux.error(new RuntimeException("boom")); }
            @Override public <T> void emitToTopic(String topicId, T payload) { }
            @Override public <T> void emitToTopic(String topicId, String eventName, T payload) { }
            @Override public <T> void emitToTopic(String topicId, String eventName, T payload, String id) { }
            @Override public <T> void emitToAll(T payload) { }
            @Override public java.util.Collection<String> currentTopics() { return java.util.List.of(); }
            @Override public <T> void emit(String topicId, T payload) { }
            @Override public <T> void emit(String topicId, String eventName, T payload) { }
            @Override public <T> void emit(String topicId, String eventName, T payload, String id) { }
            @Override public <T> void emit(T payload) { }
            @Override public void shutdown() { }
            // TopicRegistry methods
            @Override public java.util.Collection<String> topics() { return java.util.List.of(); }
            @Override public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) { return java.util.Map.of(); }
            @Override public int subscriberCount(String topic) { return 0; }
            @Override public java.util.Map<String, Integer> topicSubscriberCounts() { return java.util.Map.of(); }
        }

        @Bean
        @Primary
        SseEmitter erroringEmitter() {
            return new TestEmitter();
        }
    }
}
