package com.spectrayan.sse.server.config;

import com.spectrayan.sse.server.controller.SseEndpointHandler;
import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.template.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SseServerAutoConfigurationTests {

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
                .withPropertyValues("spectrayan.sse.server.enabled=true");
    }

    @Test
    void defaultBeansArePresent() {
        contextRunner().run(ctx -> {
            // Core
            assertThat(ctx).hasSingleBean(SseEmitter.class);
            assertThat(ctx).hasSingleBean(SseHeaderHandler.class);
            assertThat(ctx).hasSingleBean(SseServerProperties.class);
            assertThat(ctx).hasSingleBean(SseEndpointHandler.class);
            // Template + strategies
            assertThat(ctx).hasSingleBean(SseTemplate.class);
            assertThat(ctx).hasSingleBean(EventSerializer.class);
            assertThat(ctx).hasSingleBean(ClientFilter.class);
            assertThat(ctx).hasSingleBean(ReconnectPolicy.class);
            assertThat(ctx).hasSingleBean(HeartbeatPolicy.class);
            assertThat(ctx).hasSingleBean(ErrorMapper.class);
            assertThat(ctx).hasSingleBean(ConnectionRegistry.class);
            // Router
            assertThat(ctx).hasBean("sseRouterFunction");
            // Session id generator
            assertThat(ctx).hasSingleBean(com.spectrayan.sse.server.customize.SessionIdGenerator.class);
        });
    }

    @Test
    void compressionCustomizerConditional() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
                .withPropertyValues(
                        "spectrayan.sse.server.enabled=true",
                        "spectrayan.sse.server.webflux.compression=true"
                )
                .run(ctx -> {
                    // When Netty is on classpath (it is with WebFlux), bean should exist
                    assertThat(ctx.containsBean("sseCompressionCustomizer")).isTrue();
                });
    }

    @Test
    void corsFilterConditional() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
                .withPropertyValues(
                        "spectrayan.sse.server.enabled=true",
                        "spectrayan.sse.server.cors.enabled=true",
                        "spectrayan.sse.server.cors.allowed-origins=http://localhost"
                )
                .run(ctx -> {
                    assertThat(ctx.containsBean("sseCorsWebFilter")).isTrue();
                });
    }

    @Test
    void topicAndConnectionRegistriesExposedWithoutAmbiguity() {
        contextRunner().run(ctx -> {
            // The emitter implements TopicRegistry, and we also expose a delegating TopicRegistry bean.
            // Ambiguity is avoided by wiring ConnectionRegistry from SseEmitter directly.
            assertThat(ctx.containsBean("sseTopicRegistry")).isTrue();
            assertThat(ctx.getBeansOfType(ConnectionRegistry.class)).hasSize(1);
        });
    }

    @Test
    void routerRespectsBasePathOverride() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
                .withPropertyValues(
                        "spectrayan.sse.server.enabled=true",
                        "spectrayan.sse.server.base-path=/stream"
                )
                .run(ctx -> {
                    assertThat(ctx).hasBean("sseRouterFunction");
                    @SuppressWarnings("unchecked")
                    org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> router =
                            (org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse>) ctx.getBean("sseRouterFunction");
                    org.springframework.test.web.reactive.server.WebTestClient client =
                            org.springframework.test.web.reactive.server.WebTestClient.bindToRouterFunction(router).build();
                    client.get().uri("/stream/demo").exchange()
                            .expectStatus().isOk()
                            .expectHeader().valueMatches("Content-Type", ".*text/event-stream.*");
                });
    }
}
