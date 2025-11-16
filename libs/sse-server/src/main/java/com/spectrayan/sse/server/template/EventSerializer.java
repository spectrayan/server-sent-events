package com.spectrayan.sse.server.template;

import org.springframework.http.codec.ServerSentEvent;

/**
 * Strategy to convert arbitrary payloads into {@link ServerSentEvent} instances.
 */
@FunctionalInterface
public interface EventSerializer {
    ServerSentEvent<Object> toSse(Object payload, String eventName, String id);
}
