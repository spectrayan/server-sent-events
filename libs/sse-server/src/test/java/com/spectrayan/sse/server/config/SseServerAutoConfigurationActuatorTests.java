package com.spectrayan.sse.server.config;

import com.spectrayan.sse.server.actuator.SseHealthIndicator;
import com.spectrayan.sse.server.actuator.SseInfoContributor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SseServerAutoConfigurationActuatorTests {

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
                .withPropertyValues("spectrayan.sse.server.enabled=true");
    }

    @Test
    void actuatorBeansPresentByDefault() {
        contextRunner().run(ctx -> {
            assertThat(ctx).hasBean("sseHealthIndicator");
            assertThat(ctx).hasSingleBean(SseHealthIndicator.class);
            assertThat(ctx).hasBean("sseInfoContributor");
            assertThat(ctx).hasSingleBean(SseInfoContributor.class);
        });
    }

    @Test
    void actuatorBeansDisabledWhenActuatorDisabled() {
        contextRunner()
                .withPropertyValues("spectrayan.sse.server.actuator.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean("sseHealthIndicator");
                    assertThat(ctx).doesNotHaveBean("sseInfoContributor");
                });
    }

    @Test
    void healthIndicatorDisabledSelectively() {
        contextRunner()
                .withPropertyValues("spectrayan.sse.server.actuator.health=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean("sseHealthIndicator");
                    assertThat(ctx).hasBean("sseInfoContributor");
                });
    }

    @Test
    void infoContributorDisabledSelectively() {
        contextRunner()
                .withPropertyValues("spectrayan.sse.server.actuator.info=false")
                .run(ctx -> {
                    assertThat(ctx).hasBean("sseHealthIndicator");
                    assertThat(ctx).doesNotHaveBean("sseInfoContributor");
                });
    }

    @Test
    void customHealthIndicatorTakesPrecedence() {
        SseHealthIndicator customIndicator = mock(SseHealthIndicator.class);
        contextRunner()
                .withBean("sseHealthIndicator", SseHealthIndicator.class, () -> customIndicator)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SseHealthIndicator.class);
                    assertThat(ctx.getBean(SseHealthIndicator.class)).isSameAs(customIndicator);
                });
    }
}
