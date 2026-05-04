package com.spectrayan.sse.server.bridge.cloudstream;

import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.bridge.SseBridgeMessage;
import com.spectrayan.sse.server.config.SseServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Auto-configuration that activates the Spring Cloud Stream broadcast bridge
 * when both {@code spring-cloud-stream} and a binder are on the classpath.
 * <p>
 * This configuration:
 * <ol>
 *   <li>Registers a {@link CloudStreamBroadcastBridge} bean that replaces the default
 *       {@code NoOpBroadcastBridge} from {@code sse-server}.</li>
 *   <li>Registers a functional {@code Consumer<Message<SseBridgeMessage>>} bean named
 *       {@code sseBridgeConsumer} that Spring Cloud Stream auto-binds to the input
 *       channel.</li>
 * </ol>
 * <p>
 * <b>Required user configuration</b> in {@code application.yml}:
 * <pre>{@code
 * spring:
 *   cloud:
 *     function:
 *       definition: sseBridgeConsumer
 *     stream:
 *       bindings:
 *         sseBridgeConsumer-in-0:
 *           destination: sse-broadcast
 *           group: ${spring.application.name}
 * }</pre>
 * <p>
 * The outbound destination name is taken from
 * {@link SseServerProperties.Bridge#getChannelName()} (default: {@code sse-broadcast}).
 *
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(StreamBridge.class)
@ConditionalOnProperty(prefix = "spectrayan.sse.server.bridge",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class CloudStreamBridgeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CloudStreamBridgeAutoConfiguration.class);

    /**
     * The Spring Cloud Stream–backed broadcast bridge bean.
     * <p>
     * Replaces the {@code NoOpBroadcastBridge} from {@code sse-server} core because
     * this class is also conditional on {@code ConditionalOnMissingBean}, but this
     * auto-configuration is loaded <b>before</b> the core auto-configuration thanks
     * to Spring Boot's auto-configuration ordering (classpath presence of
     * {@link StreamBridge}).
     */
    @Bean
    @ConditionalOnMissingBean(SseBroadcastBridge.class)
    public CloudStreamBroadcastBridge sseBroadcastBridge(
            StreamBridge streamBridge,
            SseServerProperties properties) {

        SseServerProperties.Bridge bridgeProps = properties.getBridge();
        String instanceId = bridgeProps.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = UUID.randomUUID().toString();
            log.info("No bridge.instance-id configured; generated: {}", instanceId);
        }
        String channelName = bridgeProps.getChannelName();

        return new CloudStreamBroadcastBridge(streamBridge, instanceId, channelName);
    }

    /**
     * Functional consumer bean that Spring Cloud Stream auto-binds to the input channel.
     * <p>
     * Binding name follows Spring Cloud Stream convention:
     * {@code sseBridgeConsumer-in-0}.
     * <p>
     * When a message arrives from the shared channel (any broker), this consumer
     * extracts the {@link SseBridgeMessage} payload and delegates to
     * {@link CloudStreamBroadcastBridge#handleIncoming(SseBridgeMessage)} for
     * self-deduplication and local sink injection.
     *
     * @param bridge the cloud stream bridge bean
     * @return a consumer that processes incoming bridge messages
     */
    @Bean
    public Consumer<Message<SseBridgeMessage>> sseBridgeConsumer(
            CloudStreamBroadcastBridge bridge) {
        return message -> {
            SseBridgeMessage payload = message.getPayload();
            if (log.isTraceEnabled()) {
                log.trace("Received bridge message: topic={} origin={}",
                        payload.topic(), payload.originInstanceId());
            }
            bridge.handleIncoming(payload);
        };
    }
}
