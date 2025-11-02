package com.spectrayan.sse.server.config;

import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.util.*;

/**
 * Centralized handler for SSE header logic: MDC seeding, response header application,
 * and Problem Details header extraction. Consolidates both "log" and "response" headers
 * into a unified model based on {@link SseHeader}.
 */
public class SseHeaderHandler {

    private final List<SseHeader> headers;

    public SseHeaderHandler(SseServerProperties properties) {
        List<SseHeader> list = (properties != null ? properties.getHeaders() : null);
        this.headers = (list != null ? List.copyOf(list) : List.of());
    }

    /**
     * Populate MDC from configured headers present on the request and return a map
     * of mdcKey -> value to also be put into Reactor Context by the caller.
     */
    public Map<String, String> seedMdcFromRequest(ServerWebExchange exchange) {
        Map<String, String> added = new LinkedHashMap<>();
        if (headers.isEmpty()) return added;
        ServerHttpRequest request = exchange.getRequest();
        for (SseHeader rule : headers) {
            if (rule == null) continue;
            String mdcKey = rule.getMdcKey();
            String headerName = rule.getKey();
            if (!StringUtils.hasText(mdcKey) || !StringUtils.hasText(headerName)) continue;
            String value = request.getHeaders().getFirst(headerName);
            if (StringUtils.hasText(value)) {
                MDC.put(mdcKey, value);
                added.put(mdcKey, value);
            }
        }
        return added;
    }

    /**
     * Apply response headers according to configuration:
     * - Any header with a static {@code value} is added to the response (using responseHeaderName or key)
     * - Any header with {@code copyToResponse=true} copies the incoming request header value if present
     */
    public void applyResponseHeaders(ServerWebExchange exchange) {
        if (headers.isEmpty()) return;
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        for (SseHeader rule : headers) {
            if (rule == null) continue;
            String key = rule.getKey();
            String outName = StringUtils.hasText(rule.getResponseHeaderName()) ? rule.getResponseHeaderName() : key;
            // 1) Static value
            if (StringUtils.hasText(rule.getValue()) && StringUtils.hasText(outName)) {
                response.getHeaders().add(outName, rule.getValue());
            }
            // 2) Copy from request
            if (rule.isCopyToResponse() && StringUtils.hasText(key) && StringUtils.hasText(outName)) {
                String val = request.getHeaders().getFirst(key);
                if (StringUtils.hasText(val)) {
                    response.getHeaders().add(outName, val);
                }
            }
        }
    }

    /**
     * Build Problem Details headers map from MDC based on configuration.
     * Returns a map of original header name (key) -> value
     */
    public Map<String, String> problemHeadersFromMdc() {
        Map<String, String> out = new LinkedHashMap<>();
        if (headers.isEmpty()) return out;
        for (SseHeader rule : headers) {
            if (rule == null || !rule.isIncludeInProblem()) continue;
            String mdcKey = rule.getMdcKey();
            String headerName = rule.getKey();
            if (!StringUtils.hasText(mdcKey) || !StringUtils.hasText(headerName)) continue;
            String val = MDC.get(mdcKey);
            if (StringUtils.hasText(val)) {
                out.put(headerName, val);
            }
        }
        return out;
    }

    /**
     * Returns all MDC keys used by configured headers.
     */
    public Set<String> getMdcKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (SseHeader rule : headers) {
            if (rule == null) continue;
            String mdcKey = rule.getMdcKey();
            if (StringUtils.hasText(mdcKey)) {
                keys.add(mdcKey);
            }
        }
        return keys;
    }
}
