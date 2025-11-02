package com.spectrayan.sse.server.error;

import com.spectrayan.sse.server.config.SseServerProperties;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps server exceptions to RFC7807 Problem Details for non-SSE endpoints and initial handshake failures.
 * SSE streams themselves will emit an SSE error event where possible; this advice is a safety net
 * for standard HTTP error responses.
 */
@RestControllerAdvice
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "spectrayan.sse.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobalExceptionHandler {

    private final SseServerProperties properties;

    public GlobalExceptionHandler(SseServerProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(SseException.class)
    public ProblemDetail handleSseException(SseException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(mapStatus(ex.getCode()));
        pd.setTitle(ex.getCode().name());
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("about:blank"));
        if (ex.getTopic() != null) pd.setProperty("topic", ex.getTopic());
        pd.setProperty("code", ex.getCode().name());
        pd.setProperty("timestamp", ex.getTimestamp().toString());
        if (ex.getDetails() != null && !ex.getDetails().isEmpty()) {
            pd.setProperty("details", ex.getDetails());
        }
        addConfiguredHeaders(pd);
        return pd;
    }

    @ExceptionHandler(Throwable.class)
    public ProblemDetail handleGeneric(Throwable ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle(ErrorCode.INTERNAL_ERROR.name());
        pd.setDetail(ex.getMessage() != null ? ex.getMessage() : ex.toString());
        pd.setType(URI.create("about:blank"));
        addConfiguredHeaders(pd);
        return pd;
    }

    private void addConfiguredHeaders(ProblemDetail pd) {
        Map<String, SseServerProperties.HeaderRule> rules = properties != null ? properties.getLogHeaders() : null;
        if (rules == null || rules.isEmpty()) return;
        Map<String, String> returned = new LinkedHashMap<>();
        rules.forEach((headerName, rule) -> {
            if (rule != null && rule.isIncludeInProblem()) {
                String mdcKey = rule.getMdcKey();
                if (mdcKey != null && !mdcKey.isBlank()) {
                    String val = MDC.get(mdcKey);
                    if (val != null && !val.isBlank()) {
                        // Use original header name as the key in the response map
                        returned.put(headerName, val);
                    }
                }
            }
        });
        if (!returned.isEmpty()) {
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
}
