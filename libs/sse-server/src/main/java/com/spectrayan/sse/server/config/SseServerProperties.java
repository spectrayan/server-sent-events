package com.spectrayan.sse.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
 *   under specific MDC keys. Each header has a {@link SseHeader} with an MDC key and a flag to
 *   include that header in Problem Details (RFC7807) responses. You can also opt-in to copy the
 *   incoming header to the SSE response headers and optionally rename it.
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
 *       headers:
 *         # Request-bound headers copied to MDC, included in Problem, and echoed back on response
 *         - key: X-Request-Id
 *           mdc-key: requestId
 *           include-in-problem: true
 *           copy-to-response: true
 *           response-header-name: X-Request-Id
 *         - key: X-User-Id
 *           mdc-key: userId
 *           include-in-problem: false
 *         # Static response headers
 *         - key: Cache-Control
 *           value: no-cache
 *         - key: X-App
 *           value: sse-server
 * </pre>
 * Behavior:
 * - For each configured header with a {@code key}, if present on the request, and when an {@code mdc-key} is set,
 *   its value is placed into MDC using that key, and propagated into Reactor Context for downstream operators.
 * - Only headers with {@code include-in-problem=true} are echoed back to clients inside the
 *   Problem Details payload under {@code properties.headers} as {@code {originalHeaderName: value}}.
 * - When {@code copy-to-response=true}, the original request header value is added to the SSE
 *   response headers under the same header name or an overridden {@code response-header-name}.
 * - When a static {@code value} is specified, it is always added to the response headers using
 *   {@code response-header-name} if provided; otherwise {@code key}.
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
 * &lt;pattern&gt;%d %-5level topic=%X{topic:-na} user=%X{userId:-na} [%thread] %logger - %msg%n&lt;/pattern&gt;
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "spectrayan.sse.server")
public class SseServerProperties {

    /**
     * Unified headers configuration. Each item controls logging (MDC), inclusion in Problem Details,
     * and response header behavior (static value and/or copy-to-response).
     */
    private List<SseHeader> headers = new ArrayList<>();

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

    public void setHeaders(List<SseHeader> headers) {
        this.headers = (headers != null ? headers : new ArrayList<>());
    }
}
