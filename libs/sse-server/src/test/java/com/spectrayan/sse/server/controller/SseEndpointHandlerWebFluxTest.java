package com.spectrayan.sse.server.controller;

import com.spectrayan.sse.server.config.SseServerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.codec.ServerSentEvent;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SseEndpointHandlerWebFluxTest {

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
                .withPropertyValues(
                        "spectrayan.sse.server.enabled=true",
                        "spectrayan.sse.server.base-path=/sse",
                        "spectrayan.sse.server.stream.retry-enabled=true",
                        "spectrayan.sse.server.stream.retry=PT1S",
                        "spectrayan.sse.server.stream.map-errors-to-sse=true"
                );
    }

    @Test
    void retryPrefaceIsEmitted() {
        contextRunner().run(ctx -> {
            assertThat(ctx).hasBean("sseRouterFunction");
            @SuppressWarnings("unchecked")
            org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> router =
                    (org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse>) ctx.getBean("sseRouterFunction");

            org.springframework.test.web.reactive.server.WebTestClient client =
                    org.springframework.test.web.reactive.server.WebTestClient.bindToRouterFunction(router).build();

            var result = client.get().uri("/sse/test-topic").exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueMatches("Content-Type", ".*text/event-stream.*")
                    .returnResult(ServerSentEvent.class);

            ServerSentEvent<?> first = result.getResponseBody().blockFirst(Duration.ofSeconds(2));
            assertThat(first).isNotNull();
            assertThat(first.retry()).isNotNull();
            assertThat(first.retry()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void whenRetryDisabled_firstEventHasNoRetry() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
                .withUserConfiguration(TestNoRetryConfig.class)
                .withPropertyValues(
                        "spectrayan.sse.server.enabled=true",
                        "spectrayan.sse.server.base-path=/sse",
                        "spectrayan.sse.server.stream.retry-enabled=false",
                        "spectrayan.sse.server.stream.map-errors-to-sse=true"
                )
                .run(ctx -> {
                    // Use the auto-configured router function
                    @SuppressWarnings("unchecked")
                    org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> router =
                            (org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse>) ctx.getBean("sseRouterFunction");

                    org.springframework.test.web.reactive.server.WebTestClient client =
                            org.springframework.test.web.reactive.server.WebTestClient.bindToRouterFunction(router).build();

                    var type = new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<String>>(){};
                    var result = client.get().uri("/sse/test-no-retry").exchange()
                            .expectStatus().isOk()
                            .expectHeader().valueMatches("Content-Type", ".*text/event-stream.*")
                            .returnResult(type);

                    ServerSentEvent<String> first = result.getResponseBody().blockFirst(java.time.Duration.ofSeconds(2));
                    org.assertj.core.api.Assertions.assertThat(first).isNotNull();
                    // Our TestNoRetryConfig emitter emits a data event directly; ensure no retry control is present
                    org.assertj.core.api.Assertions.assertThat(first.retry()).isNull();
                });
    }

    @org.springframework.context.annotation.Configuration
    static class TestNoRetryConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        com.spectrayan.sse.server.emitter.SseEmitter immediateEmitter() {
            class TestEmitter implements com.spectrayan.sse.server.emitter.SseEmitter, com.spectrayan.sse.server.topic.TopicRegistry {
                @Override public reactor.core.publisher.Flux<ServerSentEvent<Object>> connect(String topic) {
                    return reactor.core.publisher.Flux.just(ServerSentEvent.builder((Object)"hello").build());
                }
                @Override public reactor.core.publisher.Flux<ServerSentEvent<Object>> connect(String topic, com.spectrayan.sse.server.session.SseSession session) { return connect(topic); }
                @Override public <T> void emitToTopic(String topicId, T payload) { }
                @Override public <T> void emitToTopic(String topicId, String eventName, T payload) { }
                @Override public <T> void emitToTopic(String topicId, String eventName, T payload, String id) { }
                @Override public <T> void emitToAll(T payload) { }
                @Override public java.util.Collection<String> currentTopics() { return java.util.List.of("test-no-retry"); }
                @Override public <T> void emit(String topicId, T payload) { }
                @Override public <T> void emit(String topicId, String eventName, T payload) { }
                @Override public <T> void emit(String topicId, String eventName, T payload, String id) { }
                @Override public <T> void emit(T payload) { }
                @Override public void shutdown() { }
                // TopicRegistry
                @Override public java.util.Collection<String> topics() { return java.util.List.of("test-no-retry"); }
                @Override public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) { return java.util.Map.of(); }
                @Override public int subscriberCount(String topic) { return 0; }
                @Override public java.util.Map<String, Integer> topicSubscriberCounts() { return java.util.Map.of(); }
            }
            return new TestEmitter();
        }

        // No need to expose ConnectionRegistry/TopicRegistry beans explicitly; the emitter implements TopicRegistry
    }
}
