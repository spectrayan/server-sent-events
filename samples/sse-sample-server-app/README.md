# SSE Sample Server App (Spring Boot)

A minimal Spring Boot WebFlux app demonstrating how to emit Server‑Sent Events (SSE) using the Spectrayan SSE Server library.

## Prerequisites
- Java 21
- Maven 3.9+

This sample depends on the `libs/sse-server` module from this repository. Install it to your local Maven cache before running:
```
# From repo root
mvn -pl libs/sse-server install
```

## Run the app
```
# From repo root
mvn -pl samples/sse-sample-server-app spring-boot:run
```
The app starts on `http://localhost:8080` by default.

## Subscribe to SSE
The library’s auto-configured controller exposes an endpoint per topic at `GET /{topic}`.

Try subscribing in a browser console:
```js
const es = new EventSource('http://localhost:8080/notifications');
es.onmessage = (e) => console.log('message', e.data);
es.addEventListener('notification', (e) => console.log('named', e.data));
```

## What gets emitted
See `NotificationScheduler` in `src/main/java/com/spectrayan/sse/sample/scheduler/NotificationScheduler.java`.
- Every 15 seconds a complex `notification` object is emitted to all connected subscribers
- Heartbeat frames are sent periodically by the library to keep connections alive

You can also inject `SseEmitter` into your own services/controllers and call:
```java
emitter.emit("hello");                 // default message event (broadcast)
emitter.emit("notifications", "notification", new Notification(...)); // named event to topic
```

## Configuration
Library properties (prefix `spectrayan.sse.server`) can be set in `application.yml` to control headers and MDC. See `libs/sse-server/README.md` for details.

## Troubleshooting
- If you get `ClassNotFound` for `sse-server`, ensure you ran `mvn -pl libs/sse-server install`
- If port 8080 is in use, set `server.port` in `application.properties`

## License
Apache-2.0

## Support
Questions or issues: support@spectrayan.com
