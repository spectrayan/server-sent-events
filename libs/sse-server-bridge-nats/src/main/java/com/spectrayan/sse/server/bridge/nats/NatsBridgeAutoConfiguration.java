package com.spectrayan.sse.server.bridge.nats;

import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.config.SseServerProperties;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Auto-configuration that activates the NATS broadcast bridge
 * when {@code io.nats.client.Connection} is on the classpath.
 * <p>
 * This replaces the default {@code NoOpBroadcastBridge} from {@code sse-server}
 * automatically. If an existing {@link Connection} bean is found (e.g. from {@code nats-spring}
 * or custom configuration), it will be used. Otherwise, a connection is established
 * using the configured properties.
 *
 * @since 2.1.0
 */
@AutoConfiguration
@ConditionalOnClass(Connection.class)
@ConditionalOnProperty(prefix = "spectrayan.sse.server.bridge",
        name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(NatsBridgeProperties.class)
public class NatsBridgeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NatsBridgeAutoConfiguration.class);

    /**
     * Auto-configures a NATS {@link Connection} bean if none is provided.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(Connection.class)
    @ConditionalOnProperty(prefix = "spectrayan.sse.server.bridge.nats",
            name = "enabled", havingValue = "true", matchIfMissing = true)
    public Connection natsConnection(NatsBridgeProperties natsProps) throws Exception {
        Options.Builder optionsBuilder = new Options.Builder()
                .server(natsProps.getServer())
                .connectionTimeout(natsProps.getConnectionTimeout())
                .reconnectWait(natsProps.getReconnectWait())
                .maxReconnects(natsProps.getMaxReconnect());

        if (natsProps.getToken() != null && !natsProps.getToken().isBlank()) {
            optionsBuilder.token(natsProps.getToken().toCharArray());
        } else if (natsProps.getUsername() != null && !natsProps.getUsername().isBlank()) {
            char[] pwd = natsProps.getPassword() != null ? natsProps.getPassword().toCharArray() : new char[0];
            optionsBuilder.userInfo(natsProps.getUsername(), new String(pwd));
        } else if (natsProps.getCredentialsFile() != null && !natsProps.getCredentialsFile().isBlank()) {
            optionsBuilder.authHandler(Nats.credentials(natsProps.getCredentialsFile()));
        }

        log.info("Connecting to NATS server at {} for SSE broadcast bridge", natsProps.getServer());
        return Nats.connect(optionsBuilder.build());
    }

    /**
     * Auto-configures the {@link NatsBroadcastBridge} bean.
     */
    @Bean
    @ConditionalOnMissingBean(SseBroadcastBridge.class)
    @ConditionalOnProperty(prefix = "spectrayan.sse.server.bridge.nats",
            name = "enabled", havingValue = "true", matchIfMissing = true)
    public NatsBroadcastBridge sseBroadcastBridge(
            Connection natsConnection,
            SseServerProperties properties,
            NatsBridgeProperties natsProperties) {

        SseServerProperties.Bridge bridgeProps = properties.getBridge();
        String instanceId = bridgeProps.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = UUID.randomUUID().toString().substring(0, 8);
            log.info("No bridge.instance-id configured; generated: {}", instanceId);
        }

        String subject = natsProperties.getSubject();
        if (subject == null || subject.isBlank()) {
            subject = bridgeProps.getChannelName();
        }

        JsonMapper jsonMapper = JsonMapper.builder().build();

        return new NatsBroadcastBridge(natsConnection, jsonMapper, subject, instanceId, natsProperties.getQueueGroup());
    }
}
