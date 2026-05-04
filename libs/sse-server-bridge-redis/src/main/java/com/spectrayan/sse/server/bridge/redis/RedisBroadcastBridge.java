package com.spectrayan.sse.server.bridge.redis;

import com.spectrayan.sse.server.bridge.SseBroadcastBridge;
import com.spectrayan.sse.server.bridge.SseBroadcastListener;
import com.spectrayan.sse.server.bridge.SseBridgeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import reactor.core.Disposable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis Pub/Sub implementation of {@link SseBroadcastBridge}.
 * <p>
 * <b>Publishing</b>: Serializes {@link SseBridgeMessage} to JSON and publishes
 * to a Redis channel via {@link ReactiveStringRedisTemplate}.
 * <p>
 * <b>Receiving</b>: Uses reactive Redis Pub/Sub to listen on the same channel.
 * Incoming messages are deserialized and forwarded to the registered
 * {@link SseBroadcastListener}.
 * <p>
 * <b>Self-deduplication</b>: Each instance is identified by a unique
 * {@code instanceId}. Messages originating from this instance are filtered out
 * by comparing the {@link SseBridgeMessage#originInstanceId()}.
 * <p>
 * This bridge uses only Spring Data Redis — no Spring Cloud Stream or external
 * binder dependencies are required.
 *
 * @since 2.0.0
 */
public class RedisBroadcastBridge implements SseBroadcastBridge {

    private static final Logger log = LoggerFactory.getLogger(RedisBroadcastBridge.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final String channel;
    private final String instanceId;
    private volatile SseBroadcastListener listener;
    private volatile Disposable subscription;

    /**
     * Create a new Redis Pub/Sub bridge.
     *
     * @param redisTemplate reactive Redis template for Pub/Sub operations
     * @param jsonMapper    Jackson 3 mapper for serialization
     * @param channel       Redis channel name for the fan-out
     * @param instanceId    unique id for this instance (for self-deduplication)
     */
    public RedisBroadcastBridge(ReactiveStringRedisTemplate redisTemplate,
                                JsonMapper jsonMapper,
                                String channel,
                                String instanceId) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.channel = channel;
        this.instanceId = instanceId;
        log.info("RedisBroadcastBridge initialized: instanceId={} channel={}", instanceId, channel);

        startListening();
    }

    private void startListening() {
        this.subscription = redisTemplate
                .listenTo(ChannelTopic.of(channel))
                .map(ReactiveSubscription.Message::getMessage)
                .subscribe(json -> {
                    try {
                        SseBridgeMessage msg = jsonMapper.readValue(json, SseBridgeMessage.class);
                        handleIncoming(msg);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize Redis bridge message: {}", e.getMessage());
                    }
                }, error -> log.error("Redis Pub/Sub subscription error on channel {}: {}",
                        channel, error.getMessage()));
        log.info("Redis Pub/Sub subscription active on channel: {}", channel);
    }

    @Override
    public void publish(SseBridgeMessage message) {
        try {
            String json = jsonMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json)
                    .subscribe(
                            receivers -> {
                                if (log.isDebugEnabled()) {
                                    log.debug("Published to Redis channel={} receivers={} topic={}",
                                            channel, receivers, message.topic());
                                }
                            },
                            error -> log.warn("Failed to publish to Redis channel={}: {}",
                                    channel, error.getMessage())
                    );
        } catch (Exception e) {
            log.warn("Failed to serialize bridge message for topic {}: {}",
                    message.topic(), e.getMessage());
        }
    }

    @Override
    public void subscribe(SseBroadcastListener listener) {
        this.listener = listener;
        log.debug("SseBroadcastListener registered on RedisBroadcastBridge");
    }

    @Override
    public void close() {
        log.info("RedisBroadcastBridge closing: instanceId={}", instanceId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        this.listener = null;
    }

    /**
     * Process an incoming message from Redis Pub/Sub.
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
            log.warn("Error processing remote Redis bridge event for topic {}: {}",
                    message.topic(), t.getMessage());
        }
    }
}
