package com.spectrayan.sse.server.template;

import com.spectrayan.sse.server.session.SseSession;

import java.util.Collection;
import java.util.Map;

/**
 * Read-only view of active connections/topics exposed by the template.
 */
public interface ConnectionRegistry {
    /**
     * Return the identifiers of topics that currently have active connections.
     *
     * @return collection of active topic ids
     */
    Collection<String> topics();

    /**
     * Snapshot of active sessions for the given topic keyed by session id.
     *
     * @param topic the topic id
     * @return unmodifiable map of sessionId -> {@link SseSession}; empty if none or topic absent
     */
    Map<String, SseSession> sessions(String topic);

    /**
     * Return current active session count for the given topic.
     *
     * @param topic the topic id
     * @return number of active sessions; 0 if none or topic absent
     */
    int count(String topic);
}
