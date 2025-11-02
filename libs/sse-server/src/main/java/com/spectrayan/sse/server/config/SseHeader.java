package com.spectrayan.sse.server.config;

import lombok.Data;

/**
 * Per-header handling rule used by {@link SseServerProperties}.
 *
 * Defines how an incoming HTTP request header should be handled:
 * - copied into MDC under a configured key
 * - optionally included back in RFC7807 Problem Details responses
 * - optionally echoed back on the SSE stream HTTP response headers, with optional rename
 */
@Data
public class SseHeader {
    /**
     * The incoming/outgoing HTTP header name. Required for request-bound behavior.
     */
    private String key;

    /**
     * Optional static value. When present, this value is always added to the SSE response headers
     * under {@code responseHeaderName} if set, otherwise {@code key}.
     */
    private String value;

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

    /**
     * When true, and the header is present on the request, its value will be added to the
     * SSE HTTP response headers as well.
     */
    private boolean copyToResponse = false;

    /**
     * Optional override for the name of the response header to use when {@link #copyToResponse} is true
     * or when a static {@link #value} is configured. If blank or null, {@link #key} will be used.
     */
    private String responseHeaderName;
}
