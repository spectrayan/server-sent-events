package com.spectrayan.sse.server.template.impl;

import com.spectrayan.sse.server.template.EventSerializer;
import org.springframework.http.codec.ServerSentEvent;

/**
 * Default serializer that simply wraps the payload into an SSE frame and sets
 * optional event name and id when provided. Actual JSON/text encoding is delegated
 * to Spring's HTTP message writers.
 */
public class DefaultEventSerializer implements EventSerializer {
    @Override
    public ServerSentEvent<Object> toSse(Object payload, String eventName, String id) {
        ServerSentEvent.Builder<Object> b = ServerSentEvent.builder(payload);
        if (eventName != null) b = b.event(eventName);
        if (id != null) b = b.id(id);
        return b.build();
    }
}
