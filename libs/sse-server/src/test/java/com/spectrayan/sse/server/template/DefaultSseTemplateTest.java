package com.spectrayan.sse.server.template;

import com.spectrayan.sse.server.config.SseHeaderHandler;
import com.spectrayan.sse.server.config.SseServerProperties;
import com.spectrayan.sse.server.customize.SseEndpointCustomizer;
import com.spectrayan.sse.server.customize.SseHeaderCustomizer;
import com.spectrayan.sse.server.customize.SseStreamCustomizer;
import com.spectrayan.sse.server.emitter.SseEmitter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSseTemplateTest {

    static class FakeEmitter implements SseEmitter {
        final Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().multicast().onBackpressureBuffer();
        @Override public Flux<ServerSentEvent<Object>> connect(String topic) { return sink.asFlux(); }
        @Override public Flux<ServerSentEvent<Object>> connect(String topic, com.spectrayan.sse.server.session.SseSession session) { return sink.asFlux(); }
        @Override public <T> void emitToTopic(String topicId, T payload) { sink.tryEmitNext(ServerSentEvent.builder((Object)payload).build()); }
        @Override public <T> void emitToTopic(String topicId, String eventName, T payload) { sink.tryEmitNext(ServerSentEvent.builder((Object)payload).event(eventName).build()); }
        @Override public <T> void emitToTopic(String topicId, String eventName, T payload, String id) { sink.tryEmitNext(ServerSentEvent.builder((Object)payload).event(eventName).id(id).build()); }
        @Override public <T> void emitToAll(T payload) { sink.tryEmitNext(ServerSentEvent.builder((Object)payload).build()); }
        @Override public java.util.Collection<String> currentTopics() { return List.of("t"); }
        @Override public <T> void emit(String topicId, T payload) { emitToTopic(topicId, payload); }
        @Override public <T> void emit(String topicId, String eventName, T payload) { emitToTopic(topicId, eventName, payload); }
        @Override public <T> void emit(String topicId, String eventName, T payload, String id) { emitToTopic(topicId, eventName, payload, id); }
        @Override public <T> void emit(T payload) { emitToAll(payload); }
        @Override public void shutdown() { sink.tryEmitComplete(); }
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return null; }
            @Override public T getIfAvailable() { return null; }
            @Override public T getIfUnique() { return null; }
            @Override public java.util.stream.Stream<T> orderedStream() { return java.util.stream.Stream.empty(); }
            @Override public java.util.Iterator<T> iterator() { return List.<T>of().iterator(); }
        };
    }

    private DefaultSseTemplate newTemplate(SseEmitter emitter,
                                           ReconnectPolicy reconnect,
                                           HeartbeatPolicy heartbeat,
                                           ErrorMapper errorMapper) {
        SseServerProperties props = new SseServerProperties();
        props.getStream().setMapErrorsToSse(true);
        SseHeaderHandler headerHandler = new SseHeaderHandler(props);
        return new DefaultSseTemplate(
                emitter,
                headerHandler,
                props,
                emptyProvider(),
                emptyProvider(),
                emptyProvider(),
                e -> {},
                (exchange, topic) -> java.util.UUID.randomUUID().toString(),
                new com.spectrayan.sse.server.template.impl.DefaultEventSerializer(),
                ctx -> true,
                reconnect,
                heartbeat,
                errorMapper,
                new com.spectrayan.sse.server.template.impl.TopicConnectionRegistry(new com.spectrayan.sse.server.topic.TopicRegistry() {
                    @Override public java.util.Collection<String> topics() { return java.util.List.of("t"); }
                    @Override public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) { return java.util.Map.of(); }
                    @Override public int subscriberCount(String topic) { return 0; }
                    @Override public java.util.Map<String, Integer> topicSubscriberCounts() { return java.util.Map.of(); }
                }) // unused in tests
        );
    }

    @Test
    void reconnectPolicyPrependsRetryThenCore() {
        FakeEmitter emitter = new FakeEmitter();
        ReconnectPolicy rp = ctx -> java.util.Optional.of(1000L);
        HeartbeatPolicy hb = ctx -> Flux.never();
        DefaultSseTemplate tpl = newTemplate(emitter, rp, hb, new com.spectrayan.sse.server.template.impl.DefaultErrorMapper());

        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", Map.of(), Map.of());
        Flux<ServerSentEvent<Object>> flux = tpl.connect("t", ctx);

        StepVerifier.create(flux)
                .assertNext(sse -> {
                    assertNotNull(sse.retry());
                    assertEquals(Duration.ofMillis(1000), sse.retry());
                })
                .then(() -> emitter.sink.tryEmitNext(ServerSentEvent.builder((Object)"core").build()))
                .assertNext(sse -> assertEquals("core", sse.data()))
                .thenCancel()
                .verify();
    }

    @Test
    void heartbeatPolicyMergesEvents() {
        FakeEmitter emitter = new FakeEmitter();
        ReconnectPolicy rp = ctx -> java.util.Optional.empty();
        HeartbeatPolicy hb = ctx -> Flux.interval(Duration.ofSeconds(1)).map(i -> ServerSentEvent.builder((Object)"hb").event("heartbeat").build());
        DefaultSseTemplate tpl = newTemplate(emitter, rp, hb, new com.spectrayan.sse.server.template.impl.DefaultErrorMapper());

        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", Map.of(), Map.of());
        Flux<ServerSentEvent<Object>> flux = tpl.connect("t", ctx);

        StepVerifier.withVirtualTime(() -> flux)
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(sse -> assertEquals("heartbeat", sse.event()))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(sse -> assertEquals("heartbeat", sse.event()))
                .thenCancel()
                .verify();
    }

    @Test
    void clientFilterDenyProducesError() {
        // Build template with a client filter that denies and with mapErrorsToSse=true
        FakeEmitter emitter = new FakeEmitter();
        SseServerProperties props = new SseServerProperties();
        props.getStream().setMapErrorsToSse(true);
        SseHeaderHandler headerHandler = new SseHeaderHandler(props);

        DefaultSseTemplate tpl = new DefaultSseTemplate(
                emitter,
                headerHandler,
                props,
                emptyProvider(),
                emptyProvider(),
                emptyProvider(),
                e -> {},
                (exchange, topic) -> "sid",
                new com.spectrayan.sse.server.template.impl.DefaultEventSerializer(),
                ctx -> false, // deny
                ctx -> java.util.Optional.empty(),
                ctx -> Flux.never(),
                new com.spectrayan.sse.server.template.impl.DefaultErrorMapper(),
                new com.spectrayan.sse.server.template.impl.TopicConnectionRegistry(new com.spectrayan.sse.server.topic.TopicRegistry() {
                    @Override public java.util.Collection<String> topics() { return java.util.List.of(); }
                    @Override public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) { return java.util.Map.of(); }
                    @Override public int subscriberCount(String topic) { return 0; }
                    @Override public java.util.Map<String, Integer> topicSubscriberCounts() { return java.util.Map.of(); }
                })
        );

        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", Map.of(), Map.of());
        StepVerifier.create(tpl.connect("t", ctx))
                .expectError(com.spectrayan.sse.server.error.SseException.class)
                .verify();
    }

    @Test
    void customErrorMapperIsUsed() {
        FakeEmitter emitter = new FakeEmitter();
        // Create a template with a custom error mapper
        ErrorMapper custom = (error, ctx) -> Flux.just(ServerSentEvent.builder((Object)"x").event("oops").build());
        DefaultSseTemplate tpl = newTemplate(emitter, ctx -> java.util.Optional.empty(), ctx -> Flux.never(), custom);

        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", Map.of(), Map.of());
        Flux<ServerSentEvent<Object>> flux = tpl.connect("t", ctx);

        StepVerifier.create(flux)
                .then(() -> emitter.sink.tryEmitError(new RuntimeException("fail")))
                .assertNext(sse -> assertEquals("oops", sse.event()))
                .thenCancel()
                .verify();
    }

    @Test
    void propsBasedReconnectFallbackWhenPolicyMissing() {
        FakeEmitter emitter = new FakeEmitter();
        // Build template manually passing null for reconnect policy to trigger props fallback
        SseServerProperties props = new SseServerProperties();
        props.getStream().setRetryEnabled(true);
        props.getStream().setRetry(Duration.ofSeconds(2));
        SseHeaderHandler headerHandler = new SseHeaderHandler(props);
        DefaultSseTemplate tpl = new DefaultSseTemplate(
                emitter, headerHandler, props,
                emptyProvider(), emptyProvider(), emptyProvider(), e -> {},
                (exchange, topic) -> "sid",
                new com.spectrayan.sse.server.template.impl.DefaultEventSerializer(),
                ctx -> true,
                null, // reconnect policy missing
                ctx -> Flux.never(),
                new com.spectrayan.sse.server.template.impl.DefaultErrorMapper(),
                new com.spectrayan.sse.server.template.impl.TopicConnectionRegistry(new com.spectrayan.sse.server.topic.TopicRegistry() {
                    @Override public java.util.Collection<String> topics() { return java.util.List.of(); }
                    @Override public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) { return java.util.Map.of(); }
                    @Override public int subscriberCount(String topic) { return 0; }
                    @Override public java.util.Map<String, Integer> topicSubscriberCounts() { return java.util.Map.of(); }
                })
        );

        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", Map.of(), Map.of());
        StepVerifier.create(tpl.connect("t", ctx))
                .assertNext(sse -> assertEquals(Duration.ofSeconds(2), sse.retry()))
                .thenCancel()
                .verify();
    }

    @Test
    void heartbeatDisabledEmitsNoHeartbeatEvents() {
        FakeEmitter emitter = new FakeEmitter();
        DefaultSseTemplate tpl = newTemplate(emitter, ctx -> java.util.Optional.empty(), ctx -> Flux.never(), new com.spectrayan.sse.server.template.impl.DefaultErrorMapper());
        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", Map.of(), Map.of());
        Flux<ServerSentEvent<Object>> flux = tpl.connect("t", ctx);

        StepVerifier.withVirtualTime(() -> flux)
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(2))
                .then(() -> emitter.sink.tryEmitNext(ServerSentEvent.builder((Object)"core").build()))
                .assertNext(sse -> assertEquals("core", sse.data()))
                .thenCancel()
                .verify();
    }

    @Test
    void streamCustomizerIsApplied() {
        FakeEmitter emitter = new FakeEmitter();

        // Stream customizer that tags data with prefix
        SseStreamCustomizer customizer = (topic, exchange, stream) ->
                stream.map(sse -> ServerSentEvent.builder((Object)("tag-" + sse.data())).event(sse.event()).id(sse.id()).build());

        // ObjectProvider that returns our single customizer
        ObjectProvider<SseStreamCustomizer> customProvider = new ObjectProvider<>() {
            @Override public SseStreamCustomizer getObject(Object... args) { return customizer; }
            @Override public SseStreamCustomizer getIfAvailable() { return customizer; }
            @Override public SseStreamCustomizer getIfUnique() { return customizer; }
            @Override public java.util.stream.Stream<SseStreamCustomizer> orderedStream() { return java.util.stream.Stream.of(customizer); }
            @Override public java.util.Iterator<SseStreamCustomizer> iterator() { return java.util.List.of(customizer).iterator(); }
        };

        // Build template wiring the customizer
        SseServerProperties props = new SseServerProperties();
        props.getStream().setMapErrorsToSse(true);
        SseHeaderHandler headerHandler = new SseHeaderHandler(props);
        DefaultSseTemplate tpl = new DefaultSseTemplate(
                emitter,
                headerHandler,
                props,
                customProvider, // stream customizers
                emptyProvider(),
                emptyProvider(),
                e -> {},
                (exchange, topic) -> "sid",
                new com.spectrayan.sse.server.template.impl.DefaultEventSerializer(),
                ctx -> true,
                ctx -> java.util.Optional.empty(),
                ctx -> Flux.never(),
                new com.spectrayan.sse.server.template.impl.DefaultErrorMapper(),
                new com.spectrayan.sse.server.template.impl.TopicConnectionRegistry(new com.spectrayan.sse.server.topic.TopicRegistry() {
                    @Override public java.util.Collection<String> topics() { return java.util.List.of(); }
                    @Override public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) { return java.util.Map.of(); }
                    @Override public int subscriberCount(String topic) { return 0; }
                    @Override public java.util.Map<String, Integer> topicSubscriberCounts() { return java.util.Map.of(); }
                })
        );

        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", Map.of(), Map.of());
        Flux<ServerSentEvent<Object>> flux = tpl.connect("t", ctx);

        StepVerifier.create(flux)
                .then(() -> emitter.sink.tryEmitNext(ServerSentEvent.builder((Object)"core").build()))
                .assertNext(sse -> org.junit.jupiter.api.Assertions.assertEquals("tag-core", sse.data()))
                .thenCancel()
                .verify();
    }

    @Test
    void whenMapErrorsToSseDisabledStreamTerminatesWithError() {
        FakeEmitter emitter = new FakeEmitter();
        // Build template with mapErrorsToSse=false
        SseServerProperties props = new SseServerProperties();
        props.getStream().setMapErrorsToSse(false);
        SseHeaderHandler headerHandler = new SseHeaderHandler(props);
        DefaultSseTemplate tpl = new DefaultSseTemplate(
                emitter,
                headerHandler,
                props,
                emptyProvider(), emptyProvider(), emptyProvider(), e -> {},
                (exchange, topic) -> "sid",
                new com.spectrayan.sse.server.template.impl.DefaultEventSerializer(),
                ctx -> true,
                ctx -> java.util.Optional.empty(),
                ctx -> Flux.never(),
                new com.spectrayan.sse.server.template.impl.DefaultErrorMapper(),
                new com.spectrayan.sse.server.template.impl.TopicConnectionRegistry(new com.spectrayan.sse.server.topic.TopicRegistry() {
                    @Override public java.util.Collection<String> topics() { return java.util.List.of(); }
                    @Override public java.util.Map<String, com.spectrayan.sse.server.session.SseSession> sessions(String topic) { return java.util.Map.of(); }
                    @Override public int subscriberCount(String topic) { return 0; }
                    @Override public java.util.Map<String, Integer> topicSubscriberCounts() { return java.util.Map.of(); }
                })
        );

        SseConnectContext ctx = new SseConnectContext("t","s",null,"r", Map.of(), Map.of());
        StepVerifier.create(tpl.connect("t", ctx))
                .then(() -> emitter.sink.tryEmitError(new RuntimeException("boom")))
                .expectError(RuntimeException.class)
                .verify();
    }
}
