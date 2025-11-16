package com.spectrayan.sse.server.template;

import com.spectrayan.sse.server.config.SseServerAutoConfiguration;
import com.spectrayan.sse.server.customize.SseEndpointCustomizer;
import com.spectrayan.sse.server.customize.SseHeaderCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSseTemplateHandleWebFluxTest {

    @Test
    void handleAppliesHeaderCustomizers_andEndpointCustomizersWrapInOrder() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "spectrayan.sse.server.enabled=true",
                        "spectrayan.sse.server.base-path=/sse", // unused here
                        "spectrayan.sse.server.stream.retry-enabled=false",
                        "spectrayan.sse.server.stream.map-errors-to-sse=true"
                );

        runner.run(ctx -> {
            // Build router on-the-fly using the SseTemplate bean
            SseTemplate template = ctx.getBean(SseTemplate.class);
            var GET = org.springframework.web.reactive.function.server.RequestPredicates.GET("/tpl/{topic}");
            org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> router =
                    org.springframework.web.reactive.function.server.RouterFunctions.route(GET, template::handle);

            org.springframework.test.web.reactive.server.WebTestClient client =
                    org.springframework.test.web.reactive.server.WebTestClient.bindToRouterFunction(router).build();

            var type = new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<String>>(){};
            var result = client.get().uri("/tpl/topic1").exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("X-Test", "yes")
                    .expectHeader().valueMatches("Content-Type", ".*text/event-stream.*")
                    .returnResult(type);

            ServerSentEvent<String> first = result.getResponseBody().blockFirst(java.time.Duration.ofSeconds(2));
            assertThat(first).isNotNull();
            assertThat(first.data()).isEqualTo("data");

            // Endpoint customizers should have wrapped outermost-first => order list reflects invocation order
            List<String> order = ctx.getBean(TestConfig.CustomizerOrder.class).order;
            assertThat(order).containsExactly("c1", "c2");
        });
    }

    @Configuration
    static class TestConfig {
        static class CustomizerOrder { final List<String> order = new ArrayList<>(); }

        @Bean CustomizerOrder customizerOrder() { return new CustomizerOrder(); }

        @Bean
        com.spectrayan.sse.server.emitter.SseEmitter fakeEmitter() {
            return new com.spectrayan.sse.server.emitter.SseEmitter() {
                @Override public Flux<ServerSentEvent<Object>> connect(String topic) { return Flux.just(ServerSentEvent.builder((Object)"data").build()); }
                @Override public Flux<ServerSentEvent<Object>> connect(String topic, com.spectrayan.sse.server.session.SseSession session) { return connect(topic); }
                @Override public <T> void emitToTopic(String topicId, T payload) { }
                @Override public <T> void emitToTopic(String topicId, String eventName, T payload) { }
                @Override public <T> void emitToTopic(String topicId, String eventName, T payload, String id) { }
                @Override public <T> void emitToAll(T payload) { }
                @Override public java.util.Collection<String> currentTopics() { return java.util.List.of("topic1"); }
                @Override public <T> void emit(String topicId, T payload) { }
                @Override public <T> void emit(String topicId, String eventName, T payload) { }
                @Override public <T> void emit(String topicId, String eventName, T payload, String id) { }
                @Override public <T> void emit(T payload) { }
                @Override public void shutdown() { }
            };
        }

        @Bean
        @org.springframework.context.annotation.Primary
        com.spectrayan.sse.server.template.ConnectionRegistry testConnectionRegistry() {
            return new com.spectrayan.sse.server.template.ConnectionRegistry() {
                @Override public java.util.Collection<String> topics() { return java.util.List.of(); }
                @Override public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) { return java.util.Map.of(); }
                @Override public int count(String topic) { return 0; }
            };
        }

        @Bean
        @org.springframework.context.annotation.Primary
        com.spectrayan.sse.server.topic.TopicRegistry testTopicRegistry() {
            return new com.spectrayan.sse.server.topic.TopicRegistry() {
                @Override public java.util.Collection<String> topics() { return java.util.List.of(); }
                @Override public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) { return java.util.Map.of(); }
                @Override public int subscriberCount(String topic) { return 0; }
                @Override public java.util.Map<String, Integer> topicSubscriberCounts() { return java.util.Map.of(); }
            };
        }

        @Bean
        SseHeaderCustomizer headerCustomizer() {
            return new SseHeaderCustomizer() {
                @Override
                public void customize(ServerWebExchange exchange, HttpHeaders responseHeaders) {
                    responseHeaders.add("X-Test", "yes");
                }
            };
        }

        @Bean
        @Order(1)
        SseEndpointCustomizer endpointCustomizer1(CustomizerOrder order) {
            return (topic, exchange, next) -> {
                order.order.add("c1");
                return next.get();
            };
        }

        @Bean
        @Order(2)
        SseEndpointCustomizer endpointCustomizer2(CustomizerOrder order) {
            return (topic, exchange, next) -> {
                order.order.add("c2");
                return next.get();
            };
        }
    }
}
