package com.spectrayan.sse.server.config;

import com.spectrayan.sse.server.customize.SseCodecCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.util.List;

/**
 * Additional WebFlux configuration for the SSE library:
 * - Applies all {@link SseCodecCustomizer} beans to the shared {@link ServerCodecConfigurer}.
 */
@org.springframework.context.annotation.Configuration
@ConditionalOnClass(WebFluxConfigurer.class)
@ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SseWebFluxConfiguration implements WebFluxConfigurer {

    private final List<SseCodecCustomizer> codecCustomizers;

    public SseWebFluxConfiguration(ObjectProvider<SseCodecCustomizer> codecCustomizers) {
        this.codecCustomizers = codecCustomizers.orderedStream().toList();
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        for (SseCodecCustomizer c : codecCustomizers) {
            try {
                c.customize(configurer);
            } catch (Throwable t) {
                // best-effort; never fail app startup on customizer
            }
        }
    }
}
