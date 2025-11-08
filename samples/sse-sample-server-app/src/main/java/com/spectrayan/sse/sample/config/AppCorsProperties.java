package com.spectrayan.sse.sample.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Property-driven CORS configuration for the sample server app.
 *
 * Example in application.yml:
 * app:
 *   cors:
 *     sse:
 *       allowed-origins: ["http://localhost:4200"]
 *       allowed-methods: ["GET"]
 *       allowed-headers: ["*"]
 *       allow-credentials: false
 *       max-age: 3600
 *     api:
 *       allowed-origins: ["http://localhost:4200"]
 *       allowed-methods: ["OPTIONS","POST","GET"]
 *       allowed-headers: ["*"]
 *       allow-credentials: false
 *       max-age: 3600
 */
@ConfigurationProperties(prefix = "app.cors")
public class AppCorsProperties {

    private final Mapping sse = new Mapping();
    private final Mapping api = new Mapping();

    public Mapping getSse() { return sse; }
    public Mapping getApi() { return api; }

    public static class Mapping {
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods = new ArrayList<>();
        private List<String> allowedHeaders = new ArrayList<>();
        private Boolean allowCredentials = Boolean.FALSE;
        private Long maxAge = 3600L;

        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

        public List<String> getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }

        public List<String> getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }

        public Boolean getAllowCredentials() { return allowCredentials; }
        public void setAllowCredentials(Boolean allowCredentials) { this.allowCredentials = allowCredentials; }

        public Long getMaxAge() { return maxAge; }
        public void setMaxAge(Long maxAge) { this.maxAge = maxAge; }
    }
}
