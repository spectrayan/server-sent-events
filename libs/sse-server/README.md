<div align="center">

# ⚡ Spectrayan SSE Server

**Reactive Server-Sent Events for Spring Boot (WebFlux)**

[![Maven Central](https://img.shields.io/badge/Maven_Central-2.0.0-blue?logo=apachemaven)](https://central.sonatype.com/artifact/com.spectrayan.sse/sse-server)
[![Java](https://img.shields.io/badge/Java-21+-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Drop-in SSE infrastructure for Spring Boot. Auto-configured endpoints, topic management,
heartbeat, session tracking, metrics, and multi-pod scaling — all from a single dependency.

</div>

---

## 📦 Installation

```xml
<dependency>
  <groupId>com.spectrayan.sse</groupId>
  <artifactId>sse-server</artifactId>
  <version>2.0.0</version>
</dependency>
```

> **Local development:** `mvn -pl libs/sse-server install` from the repo root.

---

## 🚀 Quick Start

### 1. Add the dependency (above) — that's the only setup needed

Spring Boot auto-configuration registers:
- A functional router at `GET /sse/{topic}` (configurable)
- A `SseEmitter` bean for pushing events
- Heartbeat, error handling, CORS, and metrics (if Micrometer is on classpath)

### 2. Emit events from your service

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final SseEmitter emitter;

    public void orderCreated(Order order) {
        // Emit to a specific topic with a named event
        emitter.emit("orders", "orderCreated", order);

        // Broadcast to ALL connected topics
        emitter.emit(order);
    }
}
```

### 3. Clients subscribe via the auto-configured endpoint

```javascript
const es = new EventSource('http://localhost:8080/sse/orders');

// Default unnamed events
es.onmessage = (e) => console.log('Data:', JSON.parse(e.data));

// Named events
es.addEventListener('orderCreated', (e) => {
  console.log('New order:', JSON.parse(e.data));
});
```

---

## ✨ Features at a Glance

| Feature | Description |
|---------|-------------|
| **Auto-configured endpoints** | Functional router at `GET ${base-path}/{topic}` — no controllers needed |
| **Topic-based pub/sub** | Emit to specific topics or broadcast to all active topics |
| **Heartbeat events** | Periodic `event: heartbeat` frames keep connections alive through proxies |
| **Connected event** | Initial `event: connected` frame confirms the stream is established |
| **Retry directive** | Sends SSE `retry:` field for client-side reconnection timing |
| **Session tracking** | Lifecycle hooks for session join/leave with pluggable `SseSessionHook` |
| **Flexible serialization** | Pluggable `EventSerializer` for custom payload encoding |
| **MDC propagation** | Bridge Reactor Context → SLF4J MDC for structured logging in SSE handlers |
| **CORS support** | Auto-configured `CorsWebFilter` scoped to SSE endpoints |
| **Micrometer metrics** | Emit/subscribe/error counters with optional per-topic labels |
| **RFC 7807 errors** | `SseExceptionHandler` returns `application/problem+json` responses |
| **Backpressure control** | Choose `MULTICAST` (default) or `REPLAY` sinks with tunable buffer sizes |
| **Custom sink factories** | Implement `SseEmitterCustomizer` for advanced sink configuration |
| **Codec customization** | `SseCodecCustomizer` bean to tweak `ServerCodecConfigurer` |
| **Multi-pod scaling** | Pluggable `SseBroadcastBridge` SPI (v2.0.0+) |

---

## 🔧 Configuration

All properties live under `spectrayan.sse.server.*`:

```yaml
spectrayan:
  sse:
    server:
      enabled: true                  # Master switch (default: true)
      base-path: /sse                # SSE endpoint base path

      # --- Stream behavior ---
      stream:
        connected-event-enabled: true
        connected-event-name: connected
        connected-event-data: connected
        retry-enabled: true
        retry: 3s                    # SSE retry directive
        heartbeat-enabled: true
        heartbeat-interval: 15s
        heartbeat-event-name: heartbeat
        heartbeat-data: "::heartbeat::"
        map-errors-to-sse: true      # Send errors as SSE events

      # --- Topic validation ---
      topics:
        pattern: "^[A-Za-z0-9._-]+$" # Regex for valid topic names
        max-subscribers: 0            # 0 = unlimited

      # --- Emitter/sink settings ---
      emitter:
        sink-type: MULTICAST          # MULTICAST or REPLAY
        replay-size: 0                # Buffer size when sink-type=REPLAY
        emit-retries: 16              # Retry on FAIL_NON_SERIALIZED

      # --- MDC bridge ---
      mdc-bridge-enabled: true
      mdc-context-key: sseMdc

      # --- CORS ---
      cors:
        enabled: false
        allowed-origins: ["*"]
        allowed-methods: ["GET"]
        allowed-headers: ["*"]
        allow-credentials: false
        max-age: 1h

      # --- HTTP compression ---
      webflux:
        filter-order: 0
        compression: false

      # --- Metrics ---
      metrics:
        enabled: true
        per-topic: true               # Per-topic labels (disable for high cardinality)

      # --- Error handling ---
      errors:
        enabled: true
        scope: GLOBAL                 # GLOBAL or SSE

      # --- Multi-pod bridge (v2.0.0+) ---
      bridge:
        enabled: true
        channel-name: sse-broadcast
        # instance-id:                # Auto-generated UUID if omitted

      # --- Request header mapping ---
      headers:
        - key: X-Request-Id
          mdc-key: requestId
          include-in-problem: true
          copy-to-response: true
          response-header-name: X-Request-Id
        - key: Cache-Control
          value: no-cache              # Static response header
```

---

## 🔌 Extensibility

### Custom session hooks

```java
@Component
@Order(1)
public class AuditSessionHook implements SseSessionHook {
    @Override
    public void onJoin(SseSession session) {
        log.info("Client connected: session={} topic={}", session.id(), session.topic());
    }
    @Override
    public void onLeave(SseSession session, SignalType signal) {
        log.info("Client disconnected: session={} reason={}", session.id(), signal);
    }
}
```

### Custom event serializer

```java
@Bean
EventSerializer customSerializer() {
    return (payload) -> objectMapper.writeValueAsString(payload);
}
```

### Custom codec configuration

```java
@Bean
SseCodecCustomizer registerProtobuf() {
    return (codecConfigurer) -> {
        codecConfigurer.customCodecs().register(new ProtobufEncoder());
    };
}
```

### Custom sink creation

```java
@Bean
SseEmitterCustomizer replayWithHistory() {
    return (topic) -> Sinks.many().replay().limit(100);
}
```

---

## 🌐 Multi-Pod / Horizontal Scaling

> **Problem:** SSE connections are held in-memory on a single JVM. Events emitted on Pod A are invisible to clients connected to Pod B.

> **Solution:** The broadcast bridge SPI enables cross-instance event delivery through any messaging system. Choose a bridge module — zero custom code required.

### Option 1: Redis (simplest)

```xml
<!-- One dependency — that's it -->
<dependency>
  <groupId>com.spectrayan.sse</groupId>
  <artifactId>sse-server-bridge-redis</artifactId>
  <version>2.0.0</version>
</dependency>
```

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

📖 [Full Redis bridge guide →](../sse-server-bridge-redis/README.md)

### Option 2: Spring Cloud Stream (Kafka, RabbitMQ, etc.)

```xml
<dependency>
  <groupId>com.spectrayan.sse</groupId>
  <artifactId>sse-server-bridge-cloud-stream</artifactId>
  <version>2.0.0</version>
</dependency>
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-stream-binder-kafka</artifactId>
</dependency>
```

```yaml
spring:
  cloud:
    function:
      definition: sseBridgeConsumer
    stream:
      bindings:
        sseBridgeConsumer-in-0:
          destination: sse-broadcast
          group: ${spring.application.name}
```

| Broker | Just swap the binder dependency |
|--------|------|
| Apache Kafka | `spring-cloud-stream-binder-kafka` |
| RabbitMQ | `spring-cloud-stream-binder-rabbit` |
| Google Cloud Pub/Sub | `spring-cloud-gcp-pubsub-stream-binder` |
| Apache Pulsar | `spring-cloud-stream-binder-pulsar` |
| Azure Event Hubs | `spring-cloud-azure-stream-binder-eventhubs` |

📖 [Full Cloud Stream bridge guide →](../sse-server-bridge-cloud-stream/README.md)

---

## 📡 Endpoint Reference

### `GET ${base-path}/{topic}`

Subscribes to a topic and returns a `text/event-stream`.

**Response frames:**
| Frame | When | Data |
|-------|------|------|
| `event: connected` | Immediately on connect | `connected` |
| `retry: 3000` | After connected (if enabled) | — |
| `event: heartbeat` | Every 15s (configurable) | `::heartbeat::` |
| *(data frames)* | When `emitter.emit()` is called | Your payload |

---

## 🔄 Migration Notes

### v2.0.0 (current)

- **New:** Multi-pod broadcast bridge SPI — `SseBroadcastBridge` interface + `NoOpBroadcastBridge` default
- **New:** `sse-server-bridge-redis` module — Redis Pub/Sub bridge (simplest option)
- **New:** `sse-server-bridge-cloud-stream` module — Kafka, RabbitMQ, Pulsar, etc.
- **New:** `spectrayan.sse.server.bridge.*` configuration properties
- **Breaking:** `AbstractSseEmitter` constructor now accepts `SseBroadcastBridge` parameter. If you subclass `AbstractSseEmitter` directly, add the new parameter. Standard usage via `SseEmitter` bean is unaffected.

### v1.x

- Controller mode removed — library is router-only (replace `controller.*` properties with `base-path`)
- `GlobalExceptionHandler` replaced by `SseExceptionHandler` with configurable scope

---

## 🏗️ Build & Test

```bash
# From repo root
mvn -pl libs/sse-server verify

# Or via Make
make verify-mvn
```

## 📄 License

[Apache License 2.0](../../LICENSE)

## 💬 Support

Questions or issues: **support@spectrayan.com**
