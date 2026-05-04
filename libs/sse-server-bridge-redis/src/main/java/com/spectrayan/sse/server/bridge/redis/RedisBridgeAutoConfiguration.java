package com.spectrayan.sse.server.bridge.redis;

import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.config.SseServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Auto-configuration that activates the Redis Pub/Sub broadcast bridge
 * when {@code spring-boot-starter-data-redis-reactive} is on the classpath.
 * <p>
 * This replaces the default {@code NoOpBroadcastBridge} from {@code sse-server}
 * automatically — no user code required. Just add the dependency:
 * <pre>{@code
 * <dependency>
 *   <groupId>com.spectrayan.sse</groupId>
 *   <artifactId>sse-server-bridge-redis</artifactId>
 *   <version>2.0.0</version>
 * </dependency>
 * }</pre>
 * <p>
 * Configuration properties (all optional, sensible defaults):
 * <pre>{@code
 * spectrayan.sse.server.bridge.enabled=true          # default
 * spectrayan.sse.server.bridge.channel-name=sse-broadcast
 * spectrayan.sse.server.bridge.instance-id=           # auto UUID
 * }</pre>
 *
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ReactiveStringRedisTemplate.class)
@ConditionalOnProperty(prefix = "spectrayan.sse.server.bridge",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisBridgeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisBridgeAutoConfiguration.class);

    /**
     * The Redis Pub/Sub–backed broadcast bridge bean.
     * <p>
     * Replaces the {@code NoOpBroadcastBridge} from {@code sse-server} core
     * because the core registers its default with {@code @ConditionalOnMissingBean}.
     */
    @Bean
    @ConditionalOnMissingBean(SseBroadcastBridge.class)
    public RedisBroadcastBridge sseBroadcastBridge(
            ReactiveStringRedisTemplate redisTemplate,
            SseServerProperties properties) {

        SseServerProperties.Bridge bridgeProps = properties.getBridge();
        String instanceId = bridgeProps.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = UUID.randomUUID().toString().substring(0, 8);
            log.info("No bridge.instance-id configured; generated: {}", instanceId);
        }
        String channelName = bridgeProps.getChannelName();
        JsonMapper jsonMapper = JsonMapper.builder().build();

        return new RedisBroadcastBridge(redisTemplate, jsonMapper, channelName, instanceId);
    }
}
