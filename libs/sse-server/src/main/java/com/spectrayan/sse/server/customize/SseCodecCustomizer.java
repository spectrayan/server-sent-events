package com.spectrayan.sse.server.customize;

import org.springframework.http.codec.ServerCodecConfigurer;

/**
 * Hook to customize the Server-side {@link ServerCodecConfigurer} used by WebFlux.
 *
 * This allows applications to register additional encoders/decoders or tweak
 * Jackson configuration without completely replacing the configurer.
 */
public interface SseCodecCustomizer {
    void customize(ServerCodecConfigurer configurer);
}
