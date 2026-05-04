<div align="center">

# 🖥️ SSE Sample Server App

**Spring Boot WebFlux sample using the Spectrayan SSE Server library**

</div>

---

## 📋 What This Demonstrates

This is a minimal Spring Boot application that shows how to:
- ✅ Auto-configure SSE endpoints via `sse-server`
- ✅ Emit periodic events on a schedule
- ✅ Send named events (`notification`) with complex payloads
- ✅ Broadcast string messages to all connected topics
- ✅ Keep connections alive with heartbeat frames

---

## 🏁 Prerequisites

- Java 21
- Maven 3.9+

Install the `sse-server` library to your local Maven cache:
```bash
# From repo root
mvn -pl libs/sse-server install
```

---

## 🚀 Run

```bash
# From repo root
mvn -pl samples/sse-sample-server-app spring-boot:run
```

The app starts on **http://localhost:8080** by default.

---

## 📡 Try It Out

### Subscribe via browser console

```javascript
const es = new EventSource('http://localhost:8080/notifications');

// Default unnamed events
es.onmessage = (e) => console.log('message:', e.data);

// Named events
es.addEventListener('notification', (e) => console.log('notification:', JSON.parse(e.data)));
```

### Subscribe via curl

```bash
curl -N http://localhost:8080/notifications
```

---

## ⚡ What Gets Emitted

See `NotificationScheduler` in `src/main/java/.../scheduler/NotificationScheduler.java`:

| Interval | Event | Description |
|----------|-------|-------------|
| Every 15s | `notification` | Complex object with id, timestamp, and message |
| Configurable | `heartbeat` | Built-in keep-alive frame from the library |

### Emit from your own code

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final SseEmitter emitter;

    public void example() {
        // Broadcast a string message to all topics
        emitter.emit("hello");

        // Named event to a specific topic
        emitter.emit("notifications", "notification", new Notification(...));
    }
}
```

---

## ⚙️ Configuration

All library properties are under `spectrayan.sse.server.*` in `application.yml`.
See the [sse-server docs](../../libs/sse-server/README.md) for the full reference.

---

## 🔧 Troubleshooting

| Issue | Solution |
|-------|----------|
| `ClassNotFound` for sse-server | Run `mvn -pl libs/sse-server install` first |
| Port 8080 in use | Set `server.port=8081` in `application.properties` |
| No events received | Ensure you're subscribed to the correct topic name |

---

## 📄 License

[Apache License 2.0](../../LICENSE)

## 💬 Support

Questions or issues: **support@spectrayan.com**
