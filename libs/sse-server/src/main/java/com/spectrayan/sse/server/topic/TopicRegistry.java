package com.spectrayan.sse.server.topic;

import com.spectrayan.sse.server.session.SseSession;

import java.util.Collection;
import java.util.Map;

/**
 * Read-only view over active SSE topics and their sessions.
 */
public interface TopicRegistry {
    /**
     * @return currently active topic identifiers.
     */
    Collection<String> topics();

    /**
     * Returns a snapshot of active sessions for the given topic keyed by sessionId.
     * @param topic the topic id
     * @return unmodifiable map of sessionId -> SseSession; empty if none or topic not present
     */
    Map<String, SseSession> sessions(String topic);

    /**
     * @param topic topic id
     * @return current subscriber count for the topic; 0 if none or not present
     */
    int subscriberCount(String topic);

    /**
     * @return snapshot of subscriber counts per topic.
     */
    Map<String, Integer> topicSubscriberCounts();
}
