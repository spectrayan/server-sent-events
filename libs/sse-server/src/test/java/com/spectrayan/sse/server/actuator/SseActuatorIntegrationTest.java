package com.spectrayan.sse.server.actuator;

import com.spectrayan.sse.server.config.SseServerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SseActuatorIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SseServerAutoConfiguration.class))
            .withPropertyValues(
                    "spectrayan.sse.server.enabled=true",
                    "spectrayan.sse.server.base-path=/api/v1/sse",
                    "spectrayan.sse.server.actuator.enabled=true",
                    "spectrayan.sse.server.actuator.health=true",
                    "spectrayan.sse.server.actuator.info=true"
            );

    @Test
    void healthIndicatorIsWiredAndReportsHealthy() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ReactiveHealthIndicator.class);
            ReactiveHealthIndicator healthIndicator = ctx.getBean("sseHealthIndicator", ReactiveHealthIndicator.class);

            StepVerifier.create(healthIndicator.health())
                    .assertNext(health -> {
                        assertThat(health.getStatus().getCode()).isEqualTo("UP");
                        assertThat(health.getDetails()).containsEntry("activeTopics", 0);
                        assertThat(health.getDetails()).containsEntry("totalSubscribers", 0);
                        assertThat(health.getDetails()).containsEntry("bridge", "NoOpBroadcastBridge");
                        assertThat(health.getDetails()).containsEntry("clustered", false);
                    })
                    .verifyComplete();
        });
    }

    @Test
    void infoContributorIsWiredAndContributesMetadata() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(SseInfoContributor.class);
            SseInfoContributor infoContributor = ctx.getBean(SseInfoContributor.class);

            Info.Builder builder = new Info.Builder();
            infoContributor.contribute(builder);

            Info info = builder.build();
            @SuppressWarnings("unchecked")
            Map<String, Object> sseDetails = (Map<String, Object>) info.getDetails().get("sse");
            assertThat(sseDetails).isNotNull();
            assertThat(sseDetails.get("basePath")).isEqualTo("/api/v1/sse");
            assertThat(sseDetails.get("version")).isNotNull();
            assertThat(sseDetails.get("bridge")).isEqualTo("NoOpBroadcastBridge");
            assertThat(sseDetails.get("clustered")).isEqualTo(false);
        });
    }
}
