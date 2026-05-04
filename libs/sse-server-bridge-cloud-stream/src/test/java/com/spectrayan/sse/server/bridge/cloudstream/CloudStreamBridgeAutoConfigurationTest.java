package com.spectrayan.sse.server.bridge.cloudstream;

import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.config.SseServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.stream.function.StreamBridge;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that verify the auto-configuration registers beans correctly
 * and respects conditional annotations.
 */
class CloudStreamBridgeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CloudStreamBridgeAutoConfiguration.class))
            .withBean(SseServerProperties.class)
            .withBean(StreamBridge.class, () -> {
                // Provide a mock StreamBridge since we don't have a real binder
                return org.mockito.Mockito.mock(StreamBridge.class);
            });

    @Test
    void bridgeBeanIsRegisteredWhenStreamBridgeIsPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SseBroadcastBridge.class);
            assertThat(context).hasSingleBean(CloudStreamBroadcastBridge.class);
            assertThat(context.getBean(SseBroadcastBridge.class))
                    .isInstanceOf(CloudStreamBroadcastBridge.class);
        });
    }

    @Test
    void consumerBeanIsRegistered() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("sseBridgeConsumer");
        });
    }

    @Test
    void bridgeIsDisabledWhenPropertySetToFalse() {
        contextRunner
                .withPropertyValues("spectrayan.sse.server.bridge.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SseBroadcastBridge.class);
                    assertThat(context).doesNotHaveBean("sseBridgeConsumer");
                });
    }
}
