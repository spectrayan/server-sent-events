package com.spectrayan.sse.server.customize;

import org.springframework.http.ProblemDetail;

/**
 * Hook to customize mapping of errors to RFC7807 ProblemDetails.
 */
@FunctionalInterface
public interface SseErrorCustomizer {
    ProblemDetail toProblem(Throwable error);
}
