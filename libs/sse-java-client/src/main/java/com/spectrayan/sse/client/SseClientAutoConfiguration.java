package com.spectrayan.sse.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot auto-configuration for Spectrayan SseClient.
 */
@AutoConfiguration
@EnableConfigurationProperties(SseClientProperties.class)
@ConditionalOnProperty(prefix = "spectrayan.sse.client", name = "url")
public class SseClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SseClient sseClient(
        SseClientProperties properties,
        ObjectMapper objectMapper,
        WebClient.Builder webClientBuilder
    ) {
        WebClient client = webClientBuilder.baseUrl(properties.getUrl()).build();
        return DefaultSseClient.builder()
            .baseUrl(properties.getUrl())
            .webClient(client)
            .reconnection(properties.toReconnectionConfig())
            .objectMapper(objectMapper)
            .build();
    }
}