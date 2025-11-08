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
    private final AppCorsProperties corsProps;

    public SampleSseWebFluxConfigurer(AppCorsProperties corsProps) {
        this.corsProps = corsProps;
    }

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
        // Configure via properties; no hard-coded values
        // Note: SSE CORS can be configured via the library's properties (spectrayan.sse.server.cors)
        // To avoid duplication, we only register API CORS here.
        registerCors(source, "/api/**", corsProps.getApi());
    }

    private static void registerCors(UrlBasedCorsConfigurationSource source, String pattern, AppCorsProperties.Mapping m) {
        CorsConfiguration cfg = new CorsConfiguration();
        if (m.getAllowedOrigins() != null) m.getAllowedOrigins().forEach(cfg::addAllowedOrigin);
        if (m.getAllowedMethods() != null) m.getAllowedMethods().forEach(cfg::addAllowedMethod);
        if (m.getAllowedHeaders() != null) m.getAllowedHeaders().forEach(cfg::addAllowedHeader);
        if (m.getAllowCredentials() != null) cfg.setAllowCredentials(m.getAllowCredentials());
        if (m.getMaxAge() != null) cfg.setMaxAge(m.getMaxAge());
        source.registerCorsConfiguration(pattern, cfg);
    }

    @Override
    public void configureHeaders(SseServerProperties props, SseHeaderHandler handler) {
        // Example hook point for post-instantiation header tweaks; no-op for sample
        log.debug("SseWebFluxConfigurer.configureHeaders invoked (sample)");
    }
}
