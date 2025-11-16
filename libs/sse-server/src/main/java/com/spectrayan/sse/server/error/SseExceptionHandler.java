package com.spectrayan.sse.server.error;

import com.spectrayan.sse.server.config.SseHeaderHandler;
import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.config.SseServerProperties.Scope;
import com.spectrayan.sse.server.customize.SseErrorCustomizer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Global WebFlux exception handler that renders RFC7807 Problem Details responses.
 * Scope can be GLOBAL or limited to SSE base path depending on properties.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SseExceptionHandler implements WebExceptionHandler {

    private final SseHeaderHandler headerHandler;
    private final SseErrorCustomizer errorCustomizer;
    private final ServerCodecConfigurer codecConfigurer;
    private final SseServerProperties properties;
    private final Registry registry;

    public SseExceptionHandler(SseHeaderHandler headerHandler,
                               ObjectProvider<SseErrorCustomizer> errorCustomizer,
                               ServerCodecConfigurer codecConfigurer,
                               SseServerProperties properties,
                               ObjectProvider<com.spectrayan.sse.server.config.SseWebFluxConfigurer> configurers) {
        this.headerHandler = headerHandler;
        this.errorCustomizer = errorCustomizer.getIfAvailable();
        this.codecConfigurer = codecConfigurer;
        this.properties = properties;
        this.registry = new Registry();
        // Defaults
        this.registry.addMapper(ex -> {
            if (ex instanceof SseException se) {
                ProblemDetail pd = ProblemDetail.forStatus(mapStatus(se.getCode()));
                pd.setTitle(se.getCode().name());
                pd.setDetail(se.getMessage());
                pd.setType(URI.create("about:blank"));
                if (se.getTopic() != null) pd.setProperty("topic", se.getTopic());
                pd.setProperty("code", se.getCode().name());
                pd.setProperty("timestamp", se.getTimestamp().toString());
                if (se.getDetails() != null && !se.getDetails().isEmpty()) {
                    pd.setProperty("details", se.getDetails());
                }
                return pd;
            }
            return null;
        });
        this.registry.addMapper(ex -> {
            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            pd.setTitle(ErrorCode.INTERNAL_ERROR.name());
            pd.setDetail(ex.getMessage() != null ? ex.getMessage() : ex.toString());
            pd.setType(URI.create("about:blank"));
            return pd;
        });
        // Allow user configurers to contribute/override
        if (configurers != null) {
            for (var cfg : configurers.orderedStream().toList()) {
                try {
                    cfg.configureExceptionHandling(this.registry);
                } catch (Throwable t) {
                    log.debug("SseWebFluxConfigurer.configureExceptionHandling failed: {}", t.toString());
                }
            }
        }
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // Check if library error handling is enabled
        if (properties.getErrors() != null && !properties.getErrors().isEnabled()) {
            return Mono.error(ex);
        }
        // Scope decision
        if (properties.getErrors() != null && properties.getErrors().getScope() == Scope.SSE) {
            String basePath = normalizeBasePath(properties.getBasePath());
            String path = exchange.getRequest().getPath().value();
            if (!path.startsWith(basePath + "/") && !Objects.equals(path, basePath)) {
                return Mono.error(ex);
            }
        }

        try {
            ProblemDetail pd = customize(ex);
            if (pd == null) {
                pd = registry.map(ex);
            }
            addConfiguredHeaders(pd);
            // Write response with proper status and content type
            HttpStatus status = HttpStatus.valueOf(pd.getStatus());
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

            @SuppressWarnings("unchecked")
            HttpMessageWriter<ProblemDetail> writer = (HttpMessageWriter<ProblemDetail>) findWriterFor(ProblemDetail.class);
            if (writer == null) {
                // Fallback: write minimal JSON manually
                String json = "{\"title\":\"" + (pd.getTitle() != null ? pd.getTitle() : "Error") + "\",\"status\":" + pd.getStatus() + "}";
                var buffer = exchange.getResponse().bufferFactory().wrap(json.getBytes());
                return exchange.getResponse().writeWith(Mono.just(buffer));
            }
            return writer.write(Mono.just(pd),
                    ResolvableType.forClass(ProblemDetail.class),
                    MediaType.APPLICATION_PROBLEM_JSON,
                    exchange.getResponse(),
                    Map.of());
        } catch (Throwable writeFailure) {
            log.warn("Failed to render ProblemDetail: {}", writeFailure.toString());
            return Mono.error(ex);
        }
    }

    private HttpMessageWriter<?> findWriterFor(Class<?> type) {
        for (HttpMessageWriter<?> w : codecConfigurer.getWriters()) {
            try {
                if (w.canWrite(ResolvableType.forClass(type), MediaType.APPLICATION_PROBLEM_JSON)) {
                    return w;
                }
            } catch (Throwable ignored) {
            }
        }
        // Fallback to any JSON writer
        for (HttpMessageWriter<?> w : codecConfigurer.getWriters()) {
            try {
                if (w.canWrite(ResolvableType.forClass(type), MediaType.APPLICATION_JSON)) {
                    return w;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String normalizeBasePath(String basePath) {
        String bp = (StringUtils.hasText(basePath) ? basePath.trim() : "/sse");
        if (bp.endsWith("/")) bp = bp.substring(0, bp.length() - 1);
        if (!bp.startsWith("/")) bp = "/" + bp;
        return bp;
    }

    private ProblemDetail customize(Throwable ex) {
        try {
            return errorCustomizer != null ? errorCustomizer.toProblem(ex) : null;
        } catch (Throwable t) {
            log.warn("SseErrorCustomizer failed: {}", t.toString());
            return null;
        }
    }

    private void addConfiguredHeaders(ProblemDetail pd) {
        Map<String, String> returned = headerHandler.problemHeadersFromMdc();
        if (returned != null && !returned.isEmpty()) {
            pd.setProperty("headers", returned);
        }
    }

    private HttpStatus mapStatus(ErrorCode code) {
        return switch (code) {
            case INVALID_TOPIC -> HttpStatus.BAD_REQUEST;
            case TOPIC_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NO_SUBSCRIBERS -> HttpStatus.CONFLICT;
            case EMISSION_REJECTED, STREAM_TERMINATED -> HttpStatus.CONFLICT;
            case SERIALIZATION_FAILURE, HEARTBEAT_FAILURE -> HttpStatus.INTERNAL_SERVER_ERROR;
            case SUBSCRIPTION_REJECTED -> HttpStatus.FORBIDDEN;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @Getter
    public static class Registry {
        private final List<Function<Throwable, ProblemDetail>> mappers = new ArrayList<>();
        public Registry addMapper(Function<Throwable, ProblemDetail> mapper) {
            if (mapper != null) mappers.add(mapper);
            return this;
        }
        public ProblemDetail map(Throwable ex) {
            for (Function<Throwable, ProblemDetail> m : mappers) {
                try {
                    ProblemDetail pd = m.apply(ex);
                    if (pd != null) return pd;
                } catch (Throwable ignored) {
                }
            }
            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            pd.setTitle("INTERNAL_ERROR");
            pd.setDetail(ex.getMessage() != null ? ex.getMessage() : ex.toString());
            pd.setType(URI.create("about:blank"));
            return pd;
        }
    }
}
