package com.spectrayan.sse.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the Spectrayan SSE Server library.
 * Prefix: spectrayan.sse.server
 */
@Data
@ConfigurationProperties(prefix = "spectrayan.sse.server")
public class SseServerProperties {

    // --- Core switches ---
    private boolean enabled = true;

    // MDC bridge
    private boolean mdcBridgeEnabled = true;
    private String mdcContextKey = "sseMdc";

    // Headers mapping and response/static headers
    private List<SseHeader> headers = new ArrayList<>();

    // Base path for the SSE endpoint (functional router)
    private String basePath = "/sse";

    // Error handling
    private Errors errors = new Errors();

    // Stream behavior
    private Stream stream = new Stream();

    // Topic validation/limits
    private Topics topics = new Topics();

    // Emitter/sink/backpressure settings
    private Emitter emitter = new Emitter();

    // WebFlux-related settings
    private Webflux webflux = new Webflux();

    // CORS configuration
    private Cors cors = new Cors();

    public void setHeaders(List<SseHeader> headers) {
        this.headers = (headers != null ? headers : new ArrayList<>());
    }

    @Data
    public static class Errors {
        private boolean enabled = true;
        private Scope scope = Scope.GLOBAL;
    }

    public enum Scope { GLOBAL, SSE }

    @Data
    public static class Stream {
        /** Send an initial "connected" event */
        private boolean connectedEventEnabled = true;
        private String connectedEventName = "connected";
        private String connectedEventData = "connected";

        /** Emit SSE retry control line at start */
        private boolean retryEnabled = true;
        private Duration retry = Duration.ofSeconds(3);

        /** Heartbeat settings */
        private boolean heartbeatEnabled = true;
        private Duration heartbeatInterval = Duration.ofSeconds(15);
        private String heartbeatEventName = "heartbeat";
        private String heartbeatData = "::heartbeat::";

        /** When errors happen on the stream, map to SSE error events instead of terminating */
        private boolean mapErrorsToSse = true;
    }

    @Data
    public static class Topics {
        /** Regex for valid topic identifiers */
        private String pattern = "^[A-Za-z0-9._-]+$";
        /** Max subscribers per topic (<=0 means unlimited) */
        private int maxSubscribers = 0;
    }

    @Data
    public static class Emitter {
        /** Sink type: multicast or replay */
        private SinkType sinkType = SinkType.MULTICAST;
        /** Replay size when using REPLAY sink */
        private int replaySize = 0;
    }

    public enum SinkType { MULTICAST, REPLAY }

    @Data
    public static class Webflux {
        /** WebFilter order for the MDC/copy headers filter */
        private int filterOrder = 0;
        /** Enable HTTP compression for SSE responses (off by default) */
        private boolean compression = false;
    }

    @Data
    public static class Cors {
        /** Enable library-provided CORS configuration for the SSE controller only. */
        private boolean enabled = false;
        /** Comma-separated or list of allowed origins. Use "*" for all. */
        private List<String> allowedOrigins = List.of();
        /** Allowed HTTP methods for CORS (e.g., GET,POST). Defaults to GET for SSE. */
        private List<String> allowedMethods = List.of("GET");
        /** Allowed request headers. */
        private List<String> allowedHeaders = List.of("*");
        /** Exposed response headers. */
        private List<String> exposedHeaders = List.of();
        /** Whether user credentials are supported. */
        private Boolean allowCredentials = null;
        /** Max age for preflight cache. */
        private Duration maxAge = Duration.ofHours(1);
        /** Optional path pattern override. If blank, will default to base path + "/**". */
        private String pathPattern;
    }
}
