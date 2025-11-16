package com.spectrayan.sse.server.template;

import com.spectrayan.sse.server.session.SseSession;

import java.util.Collection;
import java.util.Map;

/**
 * Read-only view of active connections/topics exposed by the template.
 */
public interface ConnectionRegistry {
    Collection<String> topics();
    Map<String, SseSession> sessions(String topic);
    int count(String topic);
}
