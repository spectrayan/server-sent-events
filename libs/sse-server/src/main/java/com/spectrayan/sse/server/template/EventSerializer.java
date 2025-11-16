package com.spectrayan.sse.server.template;

import org.springframework.http.codec.ServerSentEvent;

/**
 * Strategy to convert arbitrary payloads into {@link ServerSentEvent} instances.
 */
@FunctionalInterface
public interface EventSerializer {
    /**
     * Build a {@link ServerSentEvent} using the given payload and optional fields.
     *
     * @param payload the SSE {@code data} field content (may be any object)
     * @param eventName optional {@code event} name; may be {@code null}
     * @param id optional {@code id}; may be {@code null}
     * @return a built {@link ServerSentEvent} instance
     */
    ServerSentEvent<Object> toSse(Object payload, String eventName, String id);
}
