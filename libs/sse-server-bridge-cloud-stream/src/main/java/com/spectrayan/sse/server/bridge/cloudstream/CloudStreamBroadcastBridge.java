package com.spectrayan.sse.server.bridge.cloudstream;

import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.bridge.SseBroadcastListener;
import com.spectrayan.sse.server.bridge.SseBridgeMessage;
import com.spectrayan.sse.server.config.SseServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Spring Cloud Stream implementation of {@link SseBroadcastBridge}.
 * <p>
 * <b>Publishing</b> uses {@link StreamBridge} for imperative, dynamic sending.
 * The destination name is derived from
 * {@link SseServerProperties.Bridge#getChannelName()} (default: {@code sse-broadcast}).
 * <p>
 * <b>Receiving</b> is handled by a functional {@code Consumer<Message<SseBridgeMessage>>}
 * bean registered in {@link CloudStreamBridgeAutoConfiguration}. When a message arrives
 * from the shared channel, the auto-configuration delegates to
 * {@link #handleIncoming(SseBridgeMessage)} which forwards to the registered
 * {@link SseBroadcastListener}.
 * <p>
 * <b>Self-deduplication:</b> Each instance is identified by a unique
 * {@link SseServerProperties.Bridge#getInstanceId()} (auto-generated UUID if not
 * configured). Messages originating from this instance are filtered out by comparing
 * the {@link SseBridgeMessage#originInstanceId()} before forwarding to the listener.
 * <p>
 * The actual messaging broker (Kafka, RabbitMQ, Google Cloud Pub/Sub, Pulsar,
 * Azure Event Hubs, etc.) is determined entirely by which Spring Cloud Stream
 * <b>binder</b> dependency the end user adds to their classpath. This class has
 * zero broker-specific code.
 *
 * @since 2.0.0
 */
public class CloudStreamBroadcastBridge implements SseBroadcastBridge {

    private static final Logger log = LoggerFactory.getLogger(CloudStreamBroadcastBridge.class);

    /** Header key for the originating instance id, useful for broker-level filtering. */
    static final String HEADER_ORIGIN_INSTANCE = "sse-origin-instance";
    /** Header key for the SSE topic name. */
    static final String HEADER_TOPIC = "sse-topic";

    private final StreamBridge streamBridge;
    private final String bindingName;
    private final String instanceId;
    private volatile SseBroadcastListener listener;

    /**
     * Create a new bridge backed by Spring Cloud Stream.
     *
     * @param streamBridge the Spring Cloud Stream bridge for imperative sending
     * @param instanceId   unique id for this instance (for self-deduplication)
     * @param channelName  the binding/destination name to send to
     */
    public CloudStreamBroadcastBridge(StreamBridge streamBridge,
                                      String instanceId,
                                      String channelName) {
        this.streamBridge = streamBridge;
        this.instanceId = instanceId;
        this.bindingName = channelName;
        log.info("CloudStreamBroadcastBridge initialized: instanceId={} channel={}",
                instanceId, channelName);
    }

    @Override
    public void publish(SseBridgeMessage message) {
        Message<SseBridgeMessage> msg = MessageBuilder
                .withPayload(message)
                .setHeader(HEADER_ORIGIN_INSTANCE, instanceId)
                .setHeader(HEADER_TOPIC, message.topic())
                .build();

        boolean sent = streamBridge.send(bindingName, msg);
        if (!sent) {
            log.warn("StreamBridge failed to send SSE bridge message for topic {} (binding={})",
                    message.topic(), bindingName);
        } else if (log.isDebugEnabled()) {
            log.debug("Published SSE event to bridge: topic={} eventName={} id={}",
                    message.topic(), message.eventName(), message.id());
        }
    }

    @Override
    public void subscribe(SseBroadcastListener listener) {
        this.listener = listener;
        log.debug("SseBroadcastListener registered on CloudStreamBroadcastBridge");
    }

    @Override
    public void close() {
        log.info("CloudStreamBroadcastBridge closing: instanceId={}", instanceId);
        this.listener = null;
    }

    /**
     * Invoked by the functional consumer bean
     * ({@link CloudStreamBridgeAutoConfiguration#sseBridgeConsumer}) when a
     * message arrives from the shared channel.
     * <p>
     * Self-originated messages are filtered out by comparing instance ids.
     *
     * @param message the deserialized bridge message
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
            log.warn("Error processing remote SSE bridge event for topic {}: {}",
                    message.topic(), t.getMessage());
        }
    }
}
