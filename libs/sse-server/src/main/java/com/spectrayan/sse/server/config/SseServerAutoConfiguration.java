package com.spectrayan.sse.server.config;

import com.spectrayan.sse.server.controller.SseController;
import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.error.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.WebFilter;

/**
 * Spring Boot auto-configuration for the SSE server library.
 *
 * - Creates the controller, emitter, exception handler, MDC web filter, and Reactor MDC hook
 *   when {@code spectrayan.sse.server.enabled=true} (default).
 * - Beans are only created if missing to allow user overrides.
 * - Can be excluded via standard Spring Boot mechanisms (spring.autoconfigure.exclude) or by
 *   setting {@code spectrayan.sse.server.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SseServerProperties.class)
@ConditionalOnClass({ServerSentEvent.class})
public class SseServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SseEmitter sseEmitter(SseServerProperties properties) {
        return new SseEmitter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(WebFilter.class)
    public SseServerWebFilter sseServerWebFilter(SseServerProperties properties) {
        return new SseServerWebFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SseController sseController(SseEmitter emitter) {
        return new SseController(emitter);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler sseGlobalExceptionHandler(SseServerProperties properties) {
        return new GlobalExceptionHandler(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "mdc-bridge-enabled", havingValue = "true", matchIfMissing = true)
    public ReactorMdcConfiguration reactorMdcConfiguration(SseServerProperties properties) {
        // This bean registers a global Reactor hook in its @PostConstruct lifecycle
        return new ReactorMdcConfiguration(properties);
    }
}
