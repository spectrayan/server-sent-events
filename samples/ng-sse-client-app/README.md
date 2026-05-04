<div align="center">

# 📱 Angular SSE Client Sample App

**Live demo of `@spectrayan/ng-sse-client` consuming Server-Sent Events**

</div>

---

## 📋 What This Demonstrates

This is a minimal Angular application that shows how to:
- ✅ Connect to an SSE endpoint using `SseClient`
- ✅ Listen for both default `message` and named `notification` events
- ✅ Parse complex JSON payloads with type safety
- ✅ Auto-reconnect on connection drops
- ✅ Switch between multiple user streams in the UI
- ✅ Trigger server-side callbacks when notifications arrive (mark as read)

---

## 🏁 Prerequisites

- Node.js 20+
- npm
- A running SSE server (use the Spring Boot sample from this repo)

---

## 🚀 Run

### Step 1: Start the SSE server

```bash
# From repo root (in a separate terminal)
mvn -pl samples/sse-sample-server-app spring-boot:run
```

This starts the SSE server on **http://localhost:8080**.

### Step 2: Install dependencies

```bash
# From repo root
make setup
```

### Step 3: Start the Angular app

```bash
npx nx serve ng-sse-client-app
```

Open **http://localhost:4200** in your browser.

---

## 🔍 How It Works

The key file is [`src/app/app.ts`](src/app/app.ts):

```typescript
sse.stream<any>(`http://localhost:8080/${userId}`, {
  events: ['notification'],
  parse: (txt) => { try { return JSON.parse(txt); } catch { return txt; } },
  reconnection: {
    enabled: true,
    maxRetries: -1,
    initialDelayMs: 1000,
    maxDelayMs: 30000,
    backoffMultiplier: 2,
    jitterRatio: 0.2,
  },
  callbacks: [{
    eventType: 'notification',
    condition: (d) => !!(d?.id),
    apiCallback: markReadCallback,
    retry: { enabled: true, maxRetries: 3, delayMs: 1000 },
  }],
}).subscribe(event => { /* update UI */ });
```

| Feature | What happens |
|---------|-------------|
| **User selection** | Selecting a user connects to `http://localhost:8080/{userId}` |
| **Event parsing** | JSON payloads are parsed; plain strings pass through as-is |
| **Reconnection** | If the server goes down, exponential backoff kicks in automatically |
| **Callbacks** | When a `notification` event arrives with an `id`, it POSTs to `/api/notifications/mark-read` |

---

## ⚙️ Customize

| What | Where |
|------|-------|
| Server URL | Change `url` in `app.ts` |
| Event types | Modify `events: [...]` array |
| Reconnection settings | Adjust `reconnection: { ... }` |
| Callback behavior | Modify `callbacks: [...]` array |

---

## 🏗️ Build & Test

```bash
# Build
npx nx build ng-sse-client-app

# Test
npx nx test ng-sse-client-app --ci --codeCoverage=false
```

---

## 📄 License

[Apache License 2.0](../../LICENSE)

## 💬 Support

Questions or issues: **support@spectrayan.com**
