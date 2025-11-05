# Spectrayan SSE Server (Spring WebFlux)

Reactive SSE server utilities for Spring Boot (WebFlux).

- Auto-configuration creates controller, emitter, and error handler
- Topic-based streaming at `GET /{topic}` returning `text/event-stream`
- Built-in heartbeat events keep connections alive
- Optional Reactor Context → MDC bridge and flexible header handling

## Installation
Add the dependency (from Maven Central when released, or install locally first):
```xml
<dependency>
  <groupId>com.spectrayan.sse</groupId>
  <artifactId>sse-server</artifactId>
  <version>0.0.1</version>
</dependency>
```
For local development against this repo:
```
mvn -pl libs/sse-server install
```

## Quick start
Spring Boot auto-config registers a controller that exposes SSE at `/{topic}`.

Example subscription from a browser:
```js
const es = new EventSource('http://localhost:8080/notifications');
es.onmessage = (e) => console.log('message', e.data);
es.addEventListener('notification', (e) => console.log('named', e.data));
```

Emit events from application code using `SseEmitter`:
```java
import com.spectrayan.sse.server.emitter.SseEmitter;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {
  private final SseEmitter emitter;

  public void orderCreated(Order order) {
    // Broadcast a string message
    emitter.emit("Order created: " + order.id());

    // Emit a named event with a complex object
    emitter.emit("orders", "orderCreated", order);
  }
}
```

## Endpoints
- `GET /{topic}` — Subscribe to a topic. Produces `text/event-stream`.
  - Emits an initial `event: connected` frame
  - Periodic `event: heartbeat` frames with data `::heartbeat::`

## Configuration properties
Prefix: `spectrayan.sse.server.*`

```yaml
spectrayan:
  sse:
    server:
      enabled: true                # Master switch (default true)
      mdc-bridge-enabled: true     # Copy Reactor Context to MDC where marked
      mdc-context-key: sseMdc      # Context key that enables MDC bridging

      controller:
        base-path: /sse

      stream:
        connected-event-enabled: true
        connected-event-name: connected
        connected-event-data: connected
        retry-enabled: true
        retry: 3s
        heartbeat-enabled: true
        heartbeat-interval: 15s
        heartbeat-event-name: heartbeat
        heartbeat-data: "::heartbeat::"
        map-errors-to-sse: true

      topics:
        pattern: "^[A-Za-z0-9._-]+$"
        max-subscribers: 0

      emitter:
        sink-type: MULTICAST  # or REPLAY
        replay-size: 0        # used when sink-type=REPLAY

      webflux:
        filter-order: 0
        compression: false    # enable HTTP compression for SSE responses (opt-in)

      cors:
        enabled: false
        allowed-origins: ["*"]
        allowed-methods: ["GET"]
        allowed-headers: ["*"]
        exposed-headers: []
        allow-credentials: false
        max-age: 1h
        # path-pattern: 

      headers:
        # Copy incoming request headers into MDC and optionally echo to response
        - key: X-Request-Id
          mdc-key: requestId
          include-in-problem: true
          copy-to-response: true
          response-header-name: X-Request-Id
        # Static response header
        - key: Cache-Control
          value: no-cache
```

### CORS
- When `cors.enabled=true`, the library registers a `CorsWebFilter` that applies only to the SSE controller path (defaults to `<base-path>/**`).
- You can override by defining your own `CorsWebFilter` or global configuration; the library backs off if one already exists.

### HTTP compression (Reactor Netty)
- When `webflux.compression=true`, the auto-config enables server-side compression at the Netty HTTP server level.
- Note: Compression on long-lived SSE connections can increase latency and CPU usage. Keep it disabled unless you have a specific need and validate with clients and intermediaries.

### Codec customization
- Implement `SseCodecCustomizer` to tweak the shared `ServerCodecConfigurer` without replacing WebFlux config.
```java
@Bean
SseCodecCustomizer registerKotlinModule(ObjectMapper mapper) {
  return (cfg) -> {
    // example: ensure an additional encoder/decoder or tweak Jackson
    // var codecs = cfg.customCodecs();
    // codecs.register(new MyCustomEncoder());
  };
}
```

Behavior highlights:
- Only when `mdc-bridge-enabled=true` and the Reactor Context contains the marker key (default `sseMdc`) will values be copied into MDC.
- `headers[].value` always adds a static response header.
- `headers[].copy-to-response` echoes the incoming request header on the SSE response.
- `headers[].include-in-problem` includes the header/value in RFC7807 Problem responses emitted by the library.

## Error handling
The library provides a global exception handler that serializes errors as `application/problem+json`. Selected request headers can be included in `problem.properties.headers` as configured above.

## Sample application
See `samples/sse-sample-server-app` for a Spring Boot app that emits demo events on a schedule.

## Build & test
```
# From repo root
make verify-mvn
```

## License
Apache-2.0

## Support
Questions or issues: support@spectrayan.com

# Spectrayan SSE Server (Spring WebFlux)

Reactive SSE server utilities for Spring Boot (WebFlux).

- Auto-configuration creates controller, emitter, and error handler
- Topic-based streaming at `GET /{topic}` returning `text/event-stream`
- Built-in heartbeat events keep connections alive
- Optional Reactor Context → MDC bridge and flexible header handling

## Installation
Add the dependency (from Maven Central when released, or install locally first):
```xml
<dependency>
  <groupId>com.spectrayan.sse</groupId>
  <artifactId>sse-server</artifactId>
  <version>0.0.1</version>
</dependency>
```
For local development against this repo:
```
mvn -pl libs/sse-server install
```

## Quick start
Spring Boot auto-config registers a controller that exposes SSE at `/{topic}`.

Example subscription from a browser:
```js
const es = new EventSource('http://localhost:8080/notifications');
es.onmessage = (e) => console.log('message', e.data);
es.addEventListener('notification', (e) => console.log('named', e.data));
```

Emit events from application code using `SseEmitter`:
```java
import com.spectrayan.sse.server.emitter.SseEmitter;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {
  private final SseEmitter emitter;

  public void orderCreated(Order order) {
    // Broadcast a string message
    emitter.emit("Order created: " + order.id());

    // Emit a named event with a complex object
    emitter.emit("orders", "orderCreated", order);
  }
}
```

## Endpoints
- `GET /{topic}` — Subscribe to a topic. Produces `text/event-stream`.
  - Emits an initial `event: connected` frame
  - Periodic `event: heartbeat` frames with data `::heartbeat::`

## Configuration properties
Prefix: `spectrayan.sse.server.*`

```yaml
spectrayan:
  sse:
    server:
      enabled: true                # Master switch (default true)
      mdc-bridge-enabled: true     # Copy Reactor Context to MDC where marked
      mdc-context-key: sseMdc      # Context key that enables MDC bridging

      controller:
        base-path: /sse
        router-enabled: false      # Use functional router instead of annotation controller

      stream:
        connected-event-enabled: true
        connected-event-name: connected
        connected-event-data: connected
        retry-enabled: true
        retry: 3s
        heartbeat-enabled: true
        heartbeat-interval: 15s
        heartbeat-event-name: heartbeat
        heartbeat-data: "::heartbeat::"
        map-errors-to-sse: true

      topics:
        pattern: "^[A-Za-z0-9._-]+$"
        max-subscribers: 0

      emitter:
        sink-type: MULTICAST  # or REPLAY
        replay-size: 0        # used when sink-type=REPLAY

      webflux:
        filter-order: 0
        compression: false    # enable HTTP compression for SSE responses (opt-in)

      cors:
        enabled: false
        allowed-origins: ["*"]
        allowed-methods: ["GET"]
        allowed-headers: ["*"]
        exposed-headers: []
        allow-credentials: false
        max-age: 1h
        # path-pattern: 

      headers:
        # Copy incoming request headers into MDC and optionally echo to response
        - key: X-Request-Id
          mdc-key: requestId
          include-in-problem: true
          copy-to-response: true
          response-header-name: X-Request-Id
        # Static response header
        - key: Cache-Control
          value: no-cache
```

### Functional router mode
- When `controller.router-enabled=true`, the library exposes the SSE endpoint via a functional router instead of the annotation-based controller.
- The route pattern is `${spectrayan.sse.server.controller.base-path}/{topic}` and produces `text/event-stream`.
- This mode enables an additional SPI `SseEndpointCustomizer` that can wrap the whole endpoint behavior.

Example endpoint customizer:
```java
@Bean
SseEndpointCustomizer auditGate() {
  return (topic, exchange, next) -> {
    String path = exchange.getRequest().getPath().value();
    // Example: block topics under a certain prefix
    if (topic.startsWith("admin.")) {
      return Flux.error(new IllegalStateException("admin topics are forbidden"));
    }
    return next.get();
  };
}
```

### CORS
- When `cors.enabled=true`, the library registers a `CorsWebFilter` that applies only to the SSE controller path (defaults to `<base-path>/**`).
- You can override by defining your own `CorsWebFilter` or global configuration; the library backs off if one already exists.

### HTTP compression (Reactor Netty)
- When `webflux.compression=true`, the auto-config enables server-side compression at the Netty HTTP server level.
- Note: Compression on long-lived SSE connections can increase latency and CPU usage. Keep it disabled unless you have a specific need and validate with clients and intermediaries.

### Codec customization
- Implement `SseCodecCustomizer` to tweak the shared `ServerCodecConfigurer` without replacing WebFlux config.
```java
@Bean
SseCodecCustomizer registerKotlinModule(ObjectMapper mapper) {
  return (cfg) -> {
    // example: ensure an additional encoder/decoder or tweak Jackson
    // var codecs = cfg.customCodecs();
    // codecs.register(new MyCustomEncoder());
  };
}
```

Behavior highlights:
- Only when `mdc-bridge-enabled=true` and the Reactor Context contains the marker key (default `sseMdc`) will values be copied into MDC.
- `headers[].value` always adds a static response header.
- `headers[].copy-to-response` echoes the incoming request header on the SSE response.
- `headers[].include-in-problem` includes the header/value in RFC7807 Problem responses emitted by the library.

## Error handling
The library provides a global exception handler that serializes errors as `application/problem+json`. Selected request headers can be included in `problem.properties.headers` as configured above.

## Sample application
See `samples/sse-sample-server-app` for a Spring Boot app that emits demo events on a schedule.

## Build & test
```
# From repo root
make verify-mvn
```

## License
Apache-2.0

## Support
Questions or issues: support@spectrayan.com
