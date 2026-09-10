# Spectrayan SSE Java Client (`sse-client`)

[![Maven Central](https://img.shields.io/maven-central/v/com.spectrayan.sse/sse-client.svg)](https://central.sonatype.com/artifact/com.spectrayan.sse/sse-client)
[![Java Version](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Reactive, resilient, and non-blocking **Server-Sent Events (SSE)** client for Java 21+ and Spring Boot. Built on Project Reactor `Flux`, Spring WebFlux `WebClient`, and Java 21 Virtual Threads (Loom).

---

## Features

- ⚡ **Reactive by Default**: Consumes high-throughput event streams returning non-blocking Project Reactor `Flux<T>`.
- 🧵 **Java 21 Virtual Thread Ready**: Exposes blocking `streamBlocking()` methods designed for lightweight virtual thread execution.
- 🔁 **Automatic Reconnection**: Self-healing streams with configurable exponential backoff and randomized jitter:
  $$\text{delay} = \min(\text{initial} \times \text{multiplier}^{\text{attempt}}, \text{max}) \times (1 \pm \text{jitter})$$
- 🎯 **Last-Event-ID Resumption**: Transparently preserves the latest event ID and automatically sends `Last-Event-ID` on reconnect.
- 🔇 **Heartbeat Filtering**: Seamlessly ignores `:keepalive` and comment frames per the W3C SSE standard.
- 📦 **Jackson Serialization**: Direct deserialization into Java records, POJOs, or generic parameterized collections.
- 🌱 **Spring Boot Auto-Configuration**: Drop-in auto-configuration via application properties (`spectrayan.sse.client.*`).

---

## Installation

### Maven

```xml
<dependency>
    <groupId>com.spectrayan.sse</groupId>
    <artifactId>sse-client</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.spectrayan.sse:sse-client:2.0.0'
```

---

## Quick Start

### 1. Reactive Streaming with WebClient

```java
import com.spectrayan.sse.client.SseClient;
import com.spectrayan.sse.client.SseReconnectionConfig;
import reactor.core.publisher.Flux;

public class Application {
    public record Notification(String id, String message) {}

    public static void main(String[] args) {
        SseClient client = SseClient.builder()
            .baseUrl("https://api.example.com/sse/notifications")
            .reconnection(SseReconnectionConfig.builder()
                .initialDelay(Duration.ofMillis(500))
                .maxDelay(Duration.ofSeconds(15))
                .multiplier(1.5)
                .jitter(0.2)
                .maxRetries(10L)
                .build())
            .build();

        Flux<Notification> stream = client.stream("alert", Notification.class);
        stream.subscribe(
            notification -> System.out.println("Received: " + notification),
            error -> System.err.println("Stream error: " + error.getMessage())
        );
    }
}
```

---

### 2. Java 21 Virtual Threads (Blocking API)

For Loom-based microservices:

```java
Stream<Notification> stream = client.streamBlocking(Notification.class);
stream.forEach(notification -> {
    System.out.println("Processing on virtual thread: " + notification);
});
```

---

### 3. Spring Boot Auto-Configuration

In your `application.yml`:

```yaml
spectrayan:
  sse:
    client:
      url: https://api.example.com/sse/feed
      initial-delay: 500ms
      max-delay: 15s
      multiplier: 1.5
      jitter: 0.2
      max-retries: 10
```

Inject the pre-configured bean:

```java
@Service
public class LiveNotificationService {

    private final SseClient sseClient;

    public LiveNotificationService(SseClient sseClient) {
        this.sseClient = sseClient;
    }

    @PostConstruct
    public void startListening() {
        sseClient.stream(Notification.class)
            .subscribe(this::handleNotification);
    }
}
```

---

## License

Licensed under the Apache License, Version 2.0.