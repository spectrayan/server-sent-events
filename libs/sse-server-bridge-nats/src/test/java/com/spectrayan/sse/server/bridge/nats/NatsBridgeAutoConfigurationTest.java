package com.spectrayan.sse.server.bridge.nats;

import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.config.SseServerProperties;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests verifying that {@link NatsBridgeAutoConfiguration} correctly registers beans
 * and honors configuration properties.
 */
class NatsBridgeAutoConfigurationTest {

    private Connection createMockConnection() {
        Connection connection = mock(Connection.class);
        Dispatcher dispatcher = mock(Dispatcher.class);
        when(connection.createDispatcher(any())).thenReturn(dispatcher);
        return connection;
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NatsBridgeAutoConfiguration.class))
            .withBean(SseServerProperties.class)
            .withBean(Connection.class, this::createMockConnection);

    @Test
    void bridgeBeanIsRegisteredWhenConnectionIsPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SseBroadcastBridge.class);
            assertThat(context).hasSingleBean(NatsBroadcastBridge.class);
            assertThat(context.getBean(SseBroadcastBridge.class)).isInstanceOf(NatsBroadcastBridge.class);

            NatsBroadcastBridge bridge = context.getBean(NatsBroadcastBridge.class);
            assertThat(bridge.getSubject()).isEqualTo("sse-broadcast");
            assertThat(bridge.getInstanceId()).isNotBlank();
            assertThat(bridge.getQueueGroup()).isNull();
        });
    }

    @Test
    void bridgeCustomPropertiesApplied() {
        contextRunner
                .withPropertyValues(
                        "spectrayan.sse.server.bridge.nats.subject=custom.sse.events",
                        "spectrayan.sse.server.bridge.instance-id=pod-42",
                        "spectrayan.sse.server.bridge.nats.queue-group=sse-workers"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(NatsBroadcastBridge.class);
                    NatsBroadcastBridge bridge = context.getBean(NatsBroadcastBridge.class);
                    assertThat(bridge.getSubject()).isEqualTo("custom.sse.events");
                    assertThat(bridge.getInstanceId()).isEqualTo("pod-42");
                    assertThat(bridge.getQueueGroup()).isEqualTo("sse-workers");
                });
    }

    @Test
    void bridgeIsDisabledWhenGlobalBridgeDisabled() {
        contextRunner
                .withPropertyValues("spectrayan.sse.server.bridge.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SseBroadcastBridge.class);
                    assertThat(context).doesNotHaveBean(NatsBroadcastBridge.class);
                });
    }

    @Test
    void bridgeIsDisabledWhenNatsBridgeDisabled() {
        contextRunner
                .withPropertyValues("spectrayan.sse.server.bridge.nats.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SseBroadcastBridge.class);
                    assertThat(context).doesNotHaveBean(NatsBroadcastBridge.class);
                });
    }

    @Test
    void customSseBroadcastBridgePreventsAutoConfiguration() {
        SseBroadcastBridge customBridge = mock(SseBroadcastBridge.class);
        contextRunner
                .withBean("customBroadcastBridge", SseBroadcastBridge.class, () -> customBridge)
                .run(context -> {
                    assertThat(context).hasSingleBean(SseBroadcastBridge.class);
                    assertThat(context.getBean(SseBroadcastBridge.class)).isSameAs(customBridge);
                    assertThat(context).doesNotHaveBean(NatsBroadcastBridge.class);
                });
    }
}
