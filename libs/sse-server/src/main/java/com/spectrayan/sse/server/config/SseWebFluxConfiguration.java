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
 * - Invokes any {@link SseWebFluxConfigurer} to further tweak codecs.
 */
@org.springframework.context.annotation.Configuration
@ConditionalOnClass(WebFluxConfigurer.class)
@ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SseWebFluxConfiguration implements WebFluxConfigurer {

    private final List<SseCodecCustomizer> codecCustomizers;
    private final List<SseWebFluxConfigurer> configurers;

    public SseWebFluxConfiguration(ObjectProvider<SseCodecCustomizer> codecCustomizers,
                                   ObjectProvider<SseWebFluxConfigurer> configurers) {
        this.codecCustomizers = codecCustomizers.orderedStream().toList();
        this.configurers = configurers.orderedStream().toList();
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
        for (SseWebFluxConfigurer cfg : configurers) {
            try {
                cfg.configureCodecs(configurer);
            } catch (Throwable t) {
                // best-effort
            }
        }
    }
}
