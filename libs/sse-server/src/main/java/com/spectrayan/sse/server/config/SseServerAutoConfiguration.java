package com.spectrayan.sse.server.config;

import com.spectrayan.sse.server.controller.SseEndpointHandler;
import com.spectrayan.sse.server.customize.SseEmitterCustomizer;
import com.spectrayan.sse.server.customize.SseHeaderCustomizer;
import com.spectrayan.sse.server.customize.SseStreamCustomizer;
import com.spectrayan.sse.server.customize.SseErrorCustomizer;
import com.spectrayan.sse.server.customize.SseEndpointCustomizer;
import com.spectrayan.sse.server.template.*;
import com.spectrayan.sse.server.template.impl.*;
import org.springframework.context.ApplicationEventPublisher;
import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.emitter.DefaultSseEmitter;
import com.spectrayan.sse.server.error.SseExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.WebFilter;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Spring Boot auto-configuration for the SSE server library.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SseServerProperties.class)
@ConditionalOnClass({ServerSentEvent.class})
@org.springframework.context.annotation.Import(SseWebFluxConfiguration.class)
public class SseServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(NettyReactiveWebServerFactory.class)
    @ConditionalOnProperty(prefix = "spectrayan.sse.server.webflux", name = "compression", havingValue = "true")
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> sseCompressionCustomizer() {
        return factory -> factory.addServerCustomizers(httpServer -> httpServer.compress(true));
    }

    @Bean
    @ConditionalOnMissingBean
    public SseEmitter sseEmitter(SseServerProperties properties, ObjectProvider<SseEmitterCustomizer> sinkCustomizer,
                                 ObjectProvider<com.spectrayan.sse.server.customize.SseSessionHook> sessionHooks,
                                 com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator) {
        return new DefaultSseEmitter(properties, sinkCustomizer, sessionHooks, sessionIdGenerator);
    }

    @Bean
    @ConditionalOnMissingBean(EventSerializer.class)
    public EventSerializer sseEventSerializer() { return new DefaultEventSerializer(); }

    @Bean
    @ConditionalOnMissingBean(ClientFilter.class)
    public ClientFilter sseClientFilter() { return new AllowAllClientFilter(); }

    @Bean
    @ConditionalOnMissingBean(ReconnectPolicy.class)
    public ReconnectPolicy sseReconnectPolicy(SseServerProperties properties) { return new DefaultReconnectPolicy(properties); }

    @Bean
    @ConditionalOnMissingBean(ErrorMapper.class)
    public ErrorMapper sseErrorMapper() { return new DefaultErrorMapper(); }

    @Bean
    @ConditionalOnMissingBean(HeartbeatPolicy.class)
    public HeartbeatPolicy sseHeartbeatPolicy(SseServerProperties properties, EventSerializer serializer) {
        return new DefaultHeartbeatPolicy(properties, serializer);
    }

    @Bean
    @ConditionalOnMissingBean(ConnectionRegistry.class)
    public ConnectionRegistry sseConnectionRegistry(SseEmitter emitter) {
        // Default emitter also implements TopicRegistry; adapt it to ConnectionRegistry
        return new TopicConnectionRegistry((com.spectrayan.sse.server.topic.TopicRegistry) emitter);
    }

    @Bean
    @ConditionalOnMissingBean(SseTemplateBuilder.class)
    public SseTemplateBuilder sseTemplateBuilder(
            SseEmitter emitter,
            SseHeaderHandler headerHandler,
            SseServerProperties properties,
            ObjectProvider<SseStreamCustomizer> streamCustomizers,
            ObjectProvider<SseHeaderCustomizer> headerCustomizers,
            ObjectProvider<SseEndpointCustomizer> endpointCustomizers,
            ApplicationEventPublisher eventPublisher,
            com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator,
            EventSerializer serializer,
            ClientFilter clientFilter,
            ReconnectPolicy reconnectPolicy,
            HeartbeatPolicy heartbeatPolicy,
            ErrorMapper errorMapper,
            ConnectionRegistry connectionRegistry) {
        return new DefaultSseTemplateBuilder(
                emitter,
                headerHandler,
                properties,
                streamCustomizers,
                headerCustomizers,
                endpointCustomizers,
                eventPublisher,
                sessionIdGenerator,
                serializer,
                clientFilter,
                reconnectPolicy,
                heartbeatPolicy,
                errorMapper,
                connectionRegistry
        );
    }

    @Bean
    @ConditionalOnMissingBean(SseTemplate.class)
    public SseTemplate sseTemplate(SseTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(com.spectrayan.sse.server.customize.SessionIdGenerator.class)
    public com.spectrayan.sse.server.customize.SessionIdGenerator sseSessionIdGenerator() {
        return new com.spectrayan.sse.server.customize.UuidSessionIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(CorsWebFilter.class)
    @ConditionalOnProperty(prefix = "spectrayan.sse.server.cors", name = "enabled", havingValue = "true")
    public CorsWebFilter sseCorsWebFilter(SseServerProperties properties,
                                          org.springframework.beans.factory.ObjectProvider<SseWebFluxConfigurer> configurers) {
        var cors = properties.getCors();
        CorsConfiguration config = new CorsConfiguration();
        if (cors.getAllowedOrigins() != null && !cors.getAllowedOrigins().isEmpty()) {
            config.setAllowedOrigins(cors.getAllowedOrigins());
        }
        if (cors.getAllowedMethods() != null && !cors.getAllowedMethods().isEmpty()) {
            config.setAllowedMethods(cors.getAllowedMethods());
        }
        if (cors.getAllowedHeaders() != null && !cors.getAllowedHeaders().isEmpty()) {
            config.setAllowedHeaders(cors.getAllowedHeaders());
        }
        if (cors.getExposedHeaders() != null && !cors.getExposedHeaders().isEmpty()) {
            config.setExposedHeaders(cors.getExposedHeaders());
        }
        if (cors.getAllowCredentials() != null) {
            config.setAllowCredentials(cors.getAllowCredentials());
        }
        if (cors.getMaxAge() != null) {
            config.setMaxAge(cors.getMaxAge());
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Allow user configurers to adjust
        if (configurers != null) {
            for (SseWebFluxConfigurer cfg : configurers.orderedStream().toList()) {
                try {
                    cfg.configureCors(source);
                } catch (Throwable ignored) {}
            }
        }
        String basePath = properties.getBasePath() != null ? properties.getBasePath() : "/sse";
        String pattern = (cors.getPathPattern() != null && !cors.getPathPattern().isBlank()) ? cors.getPathPattern() : (basePath.endsWith("/**") ? basePath : basePath + "/**");
        source.registerCorsConfiguration(pattern, config);
        return new CorsWebFilter(source);
    }

    @Bean
    @ConditionalOnMissingBean
    public SseHeaderHandler sseHeaderHandler(SseServerProperties properties,
                                             org.springframework.beans.factory.ObjectProvider<SseWebFluxConfigurer> configurers) {
        SseHeaderHandler handler = new SseHeaderHandler(properties);
        if (configurers != null) {
            for (SseWebFluxConfigurer cfg : configurers.orderedStream().toList()) {
                try {
                    cfg.configureHeaders(properties, handler);
                } catch (Throwable ignored) {}
            }
        }
        return handler;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(WebFilter.class)
    public SseServerWebFilter sseServerWebFilter(SseServerProperties properties, SseHeaderHandler headerHandler) {
        return new SseServerWebFilter(properties, headerHandler);
    }

    @Bean
    @ConditionalOnMissingBean(SseEndpointHandler.class)
    public SseEndpointHandler sseEndpointHandler(SseEmitter emitter,
                                                 SseHeaderHandler headerHandler,
                                                 SseServerProperties properties,
                                                 ObjectProvider<SseStreamCustomizer> streamCustomizers,
                                                 ObjectProvider<SseHeaderCustomizer> headerCustomizers,
                                                 ObjectProvider<SseEndpointCustomizer> endpointCustomizers,
                                                 ApplicationEventPublisher eventPublisher,
                                                 com.spectrayan.sse.server.customize.SessionIdGenerator sessionIdGenerator) {
        return new SseEndpointHandler(
                emitter,
                headerHandler,
                properties,
                streamCustomizers,
                headerCustomizers,
                endpointCustomizers,
                eventPublisher,
                sessionIdGenerator
        );
    }

    @Bean
    @ConditionalOnMissingBean(name = "sseRouterFunction")
    public org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> sseRouterFunction(
            SseEndpointHandler sseEndpointHandler,
            SseServerProperties properties) {
        String basePath = properties.getBasePath() != null ? properties.getBasePath() : "/sse";
        // Normalize: ensure no trailing slash
        if (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
        String pattern = basePath + "/{topic}";
        var GET = org.springframework.web.reactive.function.server.RequestPredicates.GET(pattern);
        return org.springframework.web.reactive.function.server.RouterFunctions.route(GET, sseEndpointHandler::handle);
    }

    @Bean
    @ConditionalOnMissingBean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(org.springframework.http.codec.ServerCodecConfigurer.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(org.springframework.http.codec.ServerCodecConfigurer.class)
    @ConditionalOnProperty(prefix = "spectrayan.sse.server.errors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SseExceptionHandler sseExceptionHandler(SseHeaderHandler headerHandler,
                                                   ObjectProvider<SseErrorCustomizer> errorCustomizer,
                                                   org.springframework.http.codec.ServerCodecConfigurer codecConfigurer,
                                                   SseServerProperties properties,
                                                   ObjectProvider<SseWebFluxConfigurer> configurers) {
        return new SseExceptionHandler(headerHandler, errorCustomizer, codecConfigurer, properties, configurers);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "mdc-bridge-enabled", havingValue = "true", matchIfMissing = true)
    public ReactorMdcConfiguration reactorMdcConfiguration(SseServerProperties properties, SseHeaderHandler headerHandler) {
        // This bean registers a global Reactor hook in its @PostConstruct lifecycle
        return new ReactorMdcConfiguration(properties, headerHandler);
    }

    @Bean
    @ConditionalOnMissingBean(com.spectrayan.sse.server.topic.TopicRegistry.class)
    public com.spectrayan.sse.server.topic.TopicRegistry sseTopicRegistry(SseEmitter emitter) {
        // Default emitter implements TopicRegistry; expose a delegating wrapper to avoid
        // the bean also being considered an SseEmitter by type resolution
        return new com.spectrayan.sse.server.topic.DelegatingTopicRegistry(
                (com.spectrayan.sse.server.topic.TopicRegistry) emitter
        );
    }
}
