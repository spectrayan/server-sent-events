package com.spectrayan.sse.sample.config;

import com.spectrayan.sse.server.config.SseHeaderHandler;
import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.config.SseWebFluxConfigurer;
import com.spectrayan.sse.server.error.SseExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.net.URI;

/**
 * Example configurer demonstrating how applications can tweak the SSE library
 * without replacing auto-configuration.
 */
@Configuration
public class SampleSseWebFluxConfigurer implements SseWebFluxConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SampleSseWebFluxConfigurer.class);

    @Override
    public void configureExceptionHandling(SseExceptionHandler.Registry registry) {
        // Example: Map IllegalArgumentException to 400 with a custom title
        registry.addMapper(ex -> {
            if (ex instanceof IllegalArgumentException iae) {
                ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
                pd.setTitle("INVALID_ARGUMENT");
                pd.setDetail(iae.getMessage());
                pd.setType(URI.create("about:blank"));
                return pd;
            }
            return null;
        });
    }

    @Override
    public void configureCors(UrlBasedCorsConfigurationSource source) {
        // Example: allow a local SPA to consume SSE from /sse/**
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.addAllowedOrigin("http://localhost:4200");
        cfg.addAllowedMethod("GET");
        cfg.addAllowedHeader("*");
        cfg.setAllowCredentials(false);
        // this registration is additive; the library will also register its own pattern
        source.registerCorsConfiguration("/sse/**", cfg);
    }

    @Override
    public void configureHeaders(SseServerProperties props, SseHeaderHandler handler) {
        // Example hook point for post-instantiation header tweaks; no-op for sample
        log.debug("SseWebFluxConfigurer.configureHeaders invoked (sample)");
    }
}
