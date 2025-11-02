package com.spectrayan.sse.server.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * WebFilter that copies configured request headers into the logging MDC and propagates them
 * via Reactor Context for downstream reactive chains. Correlation ID handling has been removed;
 * only explicitly configured headers are managed here.
 * <p>
 * Configuration is provided via unified {@link SseServerProperties#getHeaders()} list.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SseServerWebFilter implements WebFilter {

    private final SseServerProperties properties;
    private final SseHeaderHandler headerHandler;

    public SseServerWebFilter(SseServerProperties properties, SseHeaderHandler headerHandler) {
        this.properties = properties;
        this.headerHandler = headerHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // If MDC bridge is disabled, do nothing special.
        if (properties == null || !properties.isMdcBridgeEnabled()) {
            return chain.filter(exchange);
        }
        // Prepare and set MDC values for this request thread based on configured headers
        final Map<String, String> addedToMdc = addToMDC(exchange);

        return chain.filter(exchange)
                // Make them available for the rest of the reactive pipeline via Reactor Context
                .contextWrite(ctx -> {
                    // Mark this reactive chain as eligible for MDC bridging (scoped activation)
                    var c = ctx.put(properties.getMdcContextKey(), Boolean.TRUE);
                    for (Map.Entry<String, String> e : addedToMdc.entrySet()) {
                        c = c.put(e.getKey(), e.getValue());
                    }
                    return c;
                })
                // Clear MDC at the very end
                .doFinally(sig -> {
                    for (String key : addedToMdc.keySet()) {
                        MDC.remove(key);
                    }
                });
    }

    private Map<String, String> addToMDC(ServerWebExchange exchange) {
        return headerHandler.seedMdcFromRequest(exchange);
    }
}
