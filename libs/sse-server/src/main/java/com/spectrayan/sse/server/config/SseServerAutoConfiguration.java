package com.spectrayan.sse.server.config;

import com.spectrayan.sse.server.controller.SseController;
import com.spectrayan.sse.server.customize.SseEmitterCustomizer;
import com.spectrayan.sse.server.customize.SseHeaderCustomizer;
import com.spectrayan.sse.server.customize.SseStreamCustomizer;
import com.spectrayan.sse.server.customize.SseErrorCustomizer;
import com.spectrayan.sse.server.emitter.SseEmitter;
import com.spectrayan.sse.server.error.GlobalExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.server.WebFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import com.spectrayan.sse.server.customize.SseCodecCustomizer;

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
    public SseEmitter sseEmitter(SseServerProperties properties, ObjectProvider<SseEmitterCustomizer> sinkCustomizer) {
        return new SseEmitter(properties, sinkCustomizer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(CorsWebFilter.class)
    @ConditionalOnProperty(prefix = "spectrayan.sse.server.cors", name = "enabled", havingValue = "true")
    public CorsWebFilter sseCorsWebFilter(SseServerProperties properties) {
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
        String basePath = properties.getController() != null ? properties.getController().getBasePath() : "/sse";
        String pattern = (cors.getPathPattern() != null && !cors.getPathPattern().isBlank()) ? cors.getPathPattern() : (basePath.endsWith("/**") ? basePath : basePath + "/**");
        source.registerCorsConfiguration(pattern, config);
        return new CorsWebFilter(source);
    }

    @Bean
    @ConditionalOnMissingBean
    public SseHeaderHandler sseHeaderHandler(SseServerProperties properties) {
        return new SseHeaderHandler(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(WebFilter.class)
    public SseServerWebFilter sseServerWebFilter(SseServerProperties properties, SseHeaderHandler headerHandler) {
        return new SseServerWebFilter(properties, headerHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spectrayan.sse.server.controller", name = "router-enabled", havingValue = "false", matchIfMissing = true)
    public SseController sseController(SseEmitter emitter,
                                       SseHeaderHandler headerHandler,
                                       SseServerProperties properties,
                                       ObjectProvider<SseStreamCustomizer> streamCustomizers,
                                       ObjectProvider<SseHeaderCustomizer> headerCustomizers) {
        return new SseController(emitter, headerHandler, properties, streamCustomizers, headerCustomizers);
    }

    @Bean
    @ConditionalOnMissingBean(name = "sseRouterFunction")
    @ConditionalOnProperty(prefix = "spectrayan.sse.server.controller", name = "router-enabled", havingValue = "true")
    public org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> sseRouterFunction(
            com.spectrayan.sse.server.controller.SseEndpointHandler handler,
            SseServerProperties properties) {
        String basePath = properties.getController() != null ? properties.getController().getBasePath() : "/sse";
        // Normalize: ensure no trailing slash
        if (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
        String pattern = basePath + "/{topic}";
        var GET = org.springframework.web.reactive.function.server.RequestPredicates.GET(pattern);
        return org.springframework.web.reactive.function.server.RouterFunctions.route(GET, handler::handle);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler sseGlobalExceptionHandler(SseHeaderHandler headerHandler,
                                                            ObjectProvider<SseErrorCustomizer> errorCustomizer) {
        return new GlobalExceptionHandler(headerHandler, errorCustomizer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "mdc-bridge-enabled", havingValue = "true", matchIfMissing = true)
    public ReactorMdcConfiguration reactorMdcConfiguration(SseServerProperties properties, SseHeaderHandler headerHandler) {
        // This bean registers a global Reactor hook in its @PostConstruct lifecycle
        return new ReactorMdcConfiguration(properties, headerHandler);
    }
}
