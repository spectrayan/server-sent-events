<div align="center">

# 📡 sse-server-bridge-redis

**Redis Pub/Sub bridge for multi-pod SSE delivery**

[![Maven Central](https://img.shields.io/badge/Maven-2.0.0-C71A36?logo=apachemaven&logoColor=white)](https://central.sonatype.com/artifact/com.spectrayan.sse/sse-server-bridge-redis)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Pub%2FSub-DC382D?logo=redis&logoColor=white)](https://redis.io)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Drop-in multi-pod SSE support using Redis — no Spring Cloud Stream required.
Just add the dependency and configure Redis. Zero custom code.

</div>

---

## 🎯 The Problem

When your app runs on multiple pods behind a load balancer, SSE clients connect to **one** pod. Events emitted on a different pod are lost because `EventSource` connections are sticky.

## ✅ The Solution

This module auto-configures a Redis Pub/Sub bridge that synchronizes events across all pods:

```
  Pod A (emits event)                Pod B (SSE client connected)
       │                                       ▲
       └──► Redis Pub/Sub channel ────────────┘
            "sse-broadcast"
```

**Every pod publishes to Redis. Every pod subscribes. Self-originated messages are filtered out.**

---

## 🚀 Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.spectrayan.sse</groupId>
    <artifactId>sse-server-bridge-redis</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 2. Configure Redis

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 3. Done!

That's it. The bridge auto-configures itself. No custom code, no bean registration.

---

## ⚙️ Configuration

All properties are optional with sensible defaults:

| Property | Default | Description |
|----------|---------|-------------|
| `spectrayan.sse.server.bridge.enabled` | `true` | Enable/disable the bridge |
| `spectrayan.sse.server.bridge.channel-name` | `sse-broadcast` | Redis channel for event fan-out |
| `spectrayan.sse.server.bridge.instance-id` | *auto UUID* | Unique ID for this pod (for self-deduplication) |

### Full example

```yaml
spring:
  data:
    redis:
      host: redis.internal
      port: 6379
      password: ${REDIS_PASSWORD}

spectrayan:
  sse:
    server:
      bridge:
        enabled: true
        channel-name: sse-broadcast
        instance-id: ${HOSTNAME:}      # auto-generated if blank
```

---

## 🏗️ How It Works

```
┌─────────────────────────────────────────────────────┐
│                    Redis Server                      │
│              Channel: "sse-broadcast"                │
│                                                      │
│  subscribe ◄──────────────────────────── subscribe   │
│      │                                      │        │
└──────┼──────────────────────────────────────┼────────┘
       │                                      │
   ┌───▼───┐  publish ─────────────────► ┌───▼───┐
   │ Pod A  │                             │ Pod B  │
   │        │ ◄───────────────── publish  │        │
   │ SSE    │                             │ SSE    │
   │Clients │                             │Clients │
   └────────┘                             └────────┘
```

1. **Publish** — When a pod emits an SSE event locally, it also serializes the event to JSON and publishes to the Redis channel
2. **Subscribe** — Every pod listens on the same Redis channel via reactive Pub/Sub
3. **Filter** — Each pod skips messages it originated itself (by comparing `instanceId`)
4. **Deliver** — Remote events are injected into the local SSE sinks for delivery to connected clients

---

## 🆚 When to Use This vs. Cloud Stream Bridge

| | `sse-server-bridge-redis` | `sse-server-bridge-cloud-stream` |
|---|---|---|
| **Broker** | Redis only | Kafka, RabbitMQ, Pulsar, Google Pub/Sub, etc. |
| **Dependencies** | `spring-boot-starter-data-redis-reactive` | `spring-cloud-stream` + binder |
| **Complexity** | Minimal — just Redis | Requires Spring Cloud Stream config |
| **Best for** | Teams already using Redis | Teams using Kafka/RabbitMQ or wanting broker flexibility |
| **Durability** | Fire-and-forget (Redis Pub/Sub) | Depends on broker (Kafka = durable) |

> **Note:** Redis Pub/Sub is fire-and-forget — if a pod is down when an event is published, it won't receive it when it comes back. If you need guaranteed delivery with replay, use Kafka via the cloud-stream bridge.

---

## 🐳 Local Testing

```bash
# Start Redis
docker run -d --name redis -p 6379:6379 redis:latest

# Start Pod A on port 8080
SERVER_PORT=8080 INSTANCE_ID=pod-A mvn spring-boot:run

# Start Pod B on port 8081 (separate terminal)
SERVER_PORT=8081 INSTANCE_ID=pod-B mvn spring-boot:run
```

Events emitted on Pod A will appear on SSE clients connected to Pod B, and vice versa.

---

## 🏗️ Development

```bash
# Build
mvn -pl libs/sse-server-bridge-redis install

# Test
mvn -pl libs/sse-server-bridge-redis test
```

---

## 📄 License

[Apache License 2.0](../../LICENSE)

## 💬 Support

Questions or issues: **support@spectrayan.com**
