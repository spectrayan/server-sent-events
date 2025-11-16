package com.spectrayan.sse.server.config;

import com.spectrayan.sse.server.controller.SseEndpointHandler;
import com.spectrayan.sse.server.customize.SseEmitterCustomizer;
import com.spectrayan.sse.server.customize.SseEndpointCustomizer;
import com.spectrayan.sse.server.customize.SseHeaderCustomizer;
import com.spectrayan.sse.server.customize.SseStreamCustomizer;
import com.spectrayan.sse.server.error.SseExceptionHandler;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.function.Consumer;

/**
 * SPI similar to Spring's WebFluxConfigurer allowing users to customize SSE library behavior
 * without fully replacing auto-configuration. All methods are optional (no-op by default).
 */
public interface SseWebFluxConfigurer {

    default void configureCodecs(ServerCodecConfigurer configurer) {}

    default void configureCors(UrlBasedCorsConfigurationSource source) {}

    default void configureHeaders(SseServerProperties props, SseHeaderHandler handler) {}

    /**
     * Contribute custom exception mappings into the library's global handler.
     */
    default void configureExceptionHandling(SseExceptionHandler.Registry registry) {}

    // Optional convenience hooks to contribute customizers via code (in addition to defining beans)
    default void addStreamCustomizers(Consumer<List<SseStreamCustomizer>> collector) {}
    default void addHeaderCustomizers(Consumer<List<SseHeaderCustomizer>> collector) {}
    default void addEndpointCustomizers(Consumer<List<SseEndpointCustomizer>> collector) {}
    default void customizeEmitter(Consumer<List<SseEmitterCustomizer>> collector) {}
}
