package com.spectrayan.sse.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the Spectrayan SSE Server library.
 * <p>
 * Prefix: {@code spectrayan.sse.server}
 * <p>
 * These properties control whether the library is enabled, how logging context (MDC) is
 * populated from incoming HTTP headers, and how/when the global Reactor Context → MDC bridge
 * should be applied to reactive chains created by this library.
 * <p>
 * Key concepts:
 * - Header mapping: you can declare request headers that should be copied into the logging MDC
 *   under specific MDC keys. Each header has a {@link HeaderRule} with an MDC key and a flag to
 *   include that header in Problem Details (RFC7807) responses.
 * - Scoped MDC bridge: a global Reactor operator hook copies values from Reactor Context into MDC
 *   only when a context marker key is present. The WebFilter seeds the marker and configured
 *   header values for HTTP request flows handled by this library.
 * - Master enable switch: all beans (controller, emitter, exception handler, WebFilter, Reactor
 *   MDC hook) are created only when {@code enabled=true}.
 * <p>
 * Example (application.yml):
 * <pre>
 * spectrayan:
 *   sse:
 *     server:
 *       enabled: true
 *       mdc-bridge-enabled: true
 *       mdc-context-key: sseMdc
 *       log-headers:
 *         X-Request-Id:
 *           mdc-key: requestId
 *           include-in-problem: true
 *         X-User-Id:
 *           mdc-key: userId
 *           include-in-problem: false
 * </pre>
 * Behavior:
 * - For each configured header, if present on the request, its value is placed into MDC using the
 *   configured {@code mdc-key}, and propagated into Reactor Context for downstream operators.
 * - Only headers with {@code include-in-problem=true} are echoed back to clients inside the
 *   Problem Details payload under {@code properties.headers} as {@code {originalHeaderName: value}}.
 * - The Reactor MDC bridge activates only for chains that contain the configured context marker
 *   (default {@code sseMdc}); it can be globally disabled via {@code mdc-bridge-enabled=false}.
 * <p>
 * Disabling the library (no beans created):
 * <pre>
 * spectrayan:
 *   sse:
 *     server:
 *       enabled: false
 * </pre>
 * Alternatively, host apps may exclude the auto-configuration class via
 * {@code spring.autoconfigure.exclude=com.spectrayan.sse.server.config.SseServerAutoConfiguration}.
 * <p>
 * Tip: reference your MDC keys in Logback patterns, e.g.
 * <pre>
 * <pattern>%d %-5level topic=%X{topic:-na} user=%X{userId:-na} [%thread] %logger - %msg%n</pattern>
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "spectrayan.sse.server")
public class SseServerProperties {

    /**
     * Map of HTTP request header name -> HeaderRule describing how to handle the header.
     */
    private Map<String, HeaderRule> logHeaders = new HashMap<>();

    /**
     * Whether the global Reactor Context -> MDC bridge should be enabled. If false, the hook is not registered.
     */
    private boolean mdcBridgeEnabled = true;

    /**
     * The Reactor Context key used as a marker to activate the MDC bridge for a given reactive chain.
     * Only when this key is present in the Context will the bridge copy values to MDC.
     */
    private String mdcContextKey = "sseMdc";

    /**
     * Master feature flag. When false, the SSE server library does not initialize any beans
     * (controller, emitter, web filter, exception handler, MDC bridge, etc.). Defaults to true.
     */
    private boolean enabled = true;

    public void setLogHeaders(Map<String, HeaderRule> logHeaders) {
        this.logHeaders = (logHeaders != null ? logHeaders : new HashMap<>());
    }

    /**
     * Per-header handling rule.
     */
    @Data
    public static class HeaderRule {
        /**
         * The MDC key name under which the header value should be stored.
         * Example: mapping header {@code X-Request-Id} to MDC key {@code requestId} allows you to
         * reference it in your log pattern with {@code %X{requestId}}.
         */
        private String mdcKey;
        /**
         * Whether to include this header and value in Problem Details responses under a consolidated
         * {@code headers} map. Default: false.
         * <p>
         * When true and the header is present on the request, the exception handler will add it under
         * {@code properties.headers[<original-header-name>]} of the RFC7807 response body.
         */
        private boolean includeInProblem = false;
    }
}
