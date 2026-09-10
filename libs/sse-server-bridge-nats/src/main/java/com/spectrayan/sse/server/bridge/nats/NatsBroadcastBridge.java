package com.spectrayan.sse.server.bridge.nats;

import com.spectrayan.sse.server.bridge.SseBridgeMessage;
import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.bridge.SseBroadcastListener;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * NATS implementation of {@link SseBroadcastBridge}.
 * <p>
 * <b>Publishing</b>: Serializes {@link SseBridgeMessage} to JSON and publishes
 * to a NATS subject via {@link Connection#publish(String, byte[])}.
 * <p>
 * <b>Receiving</b>: Uses a NATS {@link Dispatcher} to listen on the configured subject.
 * Incoming messages are deserialized and forwarded to the registered
 * {@link SseBroadcastListener}.
 * <p>
 * <b>Self-deduplication</b>: Each instance is identified by a unique
 * {@code instanceId}. Messages originating from this instance are filtered out
 * by comparing the {@link SseBridgeMessage#originInstanceId()}.
 * <p>
 * <b>Queue Group</b>: By default, subscriptions are broadcast (fan-out) so that
 * all instances deliver events to their locally connected SSE clients. If an optional
 * queue group is configured, messages are load-balanced among members of the group.
 *
 * @since 2.1.0
 */
public class NatsBroadcastBridge implements SseBroadcastBridge {

    private static final Logger log = LoggerFactory.getLogger(NatsBroadcastBridge.class);

    private final Connection connection;
    private final JsonMapper jsonMapper;
    private final String subject;
    private final String instanceId;
    private final String queueGroup;
    private volatile SseBroadcastListener listener;
    private volatile Dispatcher dispatcher;

    /**
     * Create a new NATS broadcast bridge.
     *
     * @param connection NATS client connection
     * @param jsonMapper Jackson 3 mapper for serialization
     * @param subject    NATS subject name for event fan-out
     * @param instanceId unique id for this instance (for self-deduplication)
     * @param queueGroup optional queue group name (null or blank for broadcast fan-out)
     */
    public NatsBroadcastBridge(Connection connection,
                               JsonMapper jsonMapper,
                               String subject,
                               String instanceId,
                               String queueGroup) {
        this.connection = connection;
        this.jsonMapper = jsonMapper;
        this.subject = subject;
        this.instanceId = instanceId;
        this.queueGroup = (queueGroup != null && !queueGroup.isBlank()) ? queueGroup.trim() : null;

        log.info("NatsBroadcastBridge initialized: instanceId={} subject={} queueGroup={}",
                instanceId, subject, this.queueGroup);

        startListening();
    }

    private void startListening() {
        this.dispatcher = connection.createDispatcher(msg -> {
            byte[] data = msg.getData();
            if (data == null || data.length == 0) {
                return;
            }
            try {
                SseBridgeMessage bridgeMsg = jsonMapper.readValue(data, SseBridgeMessage.class);
                handleIncoming(bridgeMsg);
            } catch (Exception e) {
                log.warn("Failed to deserialize NATS bridge message on subject {}: {}",
                        subject, e.getMessage());
            }
        });

        if (queueGroup != null) {
            this.dispatcher.subscribe(subject, queueGroup);
            log.info("NATS queue subscription active on subject: {} (queueGroup: {})", subject, queueGroup);
        } else {
            this.dispatcher.subscribe(subject);
            log.info("NATS broadcast subscription active on subject: {}", subject);
        }
    }

    @Override
    public void publish(SseBridgeMessage message) {
        try {
            byte[] data = jsonMapper.writeValueAsBytes(message);
            connection.publish(subject, data);
            if (log.isDebugEnabled()) {
                log.debug("Published to NATS subject={} topic={}", subject, message.topic());
            }
        } catch (Exception e) {
            log.warn("Failed to publish bridge message to NATS subject={} for topic {}: {}",
                    subject, message.topic(), e.getMessage());
        }
    }

    @Override
    public void subscribe(SseBroadcastListener listener) {
        this.listener = listener;
        log.debug("SseBroadcastListener registered on NatsBroadcastBridge");
    }

    @Override
    public void close() {
        log.info("NatsBroadcastBridge closing: instanceId={}", instanceId);
        if (dispatcher != null) {
            try {
                connection.closeDispatcher(dispatcher);
            } catch (Exception e) {
                log.debug("Error closing NATS dispatcher: {}", e.getMessage());
            }
            this.dispatcher = null;
        }
        this.listener = null;
    }

    /**
     * Process an incoming message from NATS.
     * Self-originated messages are filtered out by comparing instance ids.
     */
    void handleIncoming(SseBridgeMessage message) {
        if (listener == null) {
            log.trace("Ignoring incoming bridge message (no listener registered): topic={}",
                    message.topic());
            return;
        }
        // Self-deduplication
        if (instanceId.equals(message.originInstanceId())) {
            log.trace("Skipping self-originated bridge message: topic={}", message.topic());
            return;
        }
        try {
            listener.onRemoteEvent(message);
            if (log.isDebugEnabled()) {
                log.debug("Delivered remote bridge event: topic={} from={}",
                        message.topic(), message.originInstanceId());
            }
        } catch (Throwable t) {
            log.warn("Error processing remote NATS bridge event for topic {}: {}",
                    message.topic(), t.getMessage());
        }
    }

    public String getSubject() {
        return subject;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getQueueGroup() {
        return queueGroup;
    }
}
