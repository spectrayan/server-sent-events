<div align="center">

# 📱 @spectrayan/ng-sse-client

**A typed, zone-aware Server-Sent Events client for Angular**

[![npm](https://img.shields.io/badge/npm-@spectrayan/ng--sse--client-CB3837?logo=npm&logoColor=white)](https://www.npmjs.com/package/@spectrayan/ng-sse-client)
[![Angular](https://img.shields.io/badge/Angular-16_to_20-DD0031?logo=angular&logoColor=white)](https://angular.dev)
[![RxJS](https://img.shields.io/badge/RxJS-7-B7178C?logo=reactivex&logoColor=white)](https://rxjs.dev)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Type-safe SSE streams with automatic reconnection, exponential backoff,
zone-optimized performance, and optional event-driven callbacks.

</div>

---

## 📦 Installation

```bash
npm install @spectrayan/ng-sse-client
```

**Peer dependencies:** Angular ≥16 \<21, RxJS 7

---

## 🚀 Quick Start

### Basic stream

```typescript
import { Component } from '@angular/core';
import { SseClient } from '@spectrayan/ng-sse-client';

@Component({
  selector: 'app-demo',
  template: `
    <ul>
      <li *ngFor="let m of messages">{{ m }}</li>
    </ul>
  `
})
export class DemoComponent {
  messages: string[] = [];

  constructor(sse: SseClient) {
    sse.stream<string>('http://localhost:8080/sse/general')
      .subscribe(m => this.messages.unshift(m));
  }
}
```

### Named events

```typescript
sse.streamEvent<Notification>('http://localhost:8080/sse/notifications', 'notification')
  .subscribe(n => console.log(n.id, n.text));
```

### Typed parsing

```typescript
sse.stream<MyEvent>('http://localhost:8080/sse/events', {
  parse: (raw) => JSON.parse(raw) as MyEvent,
}).subscribe(event => console.log(event));
```

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| **Type-safe streams** | Generic `stream<T>()` and `streamEvent<T>()` with compile-time safety |
| **Auto-reconnect** | Exponential backoff with configurable jitter, max retries, and delay limits |
| **Zone-optimized** | Network I/O and parsing run outside Angular zone; emissions re-enter for change detection |
| **Named events** | Subscribe to specific SSE event types (`notification`, `orderUpdate`, etc.) |
| **Last-Event-ID** | Automatic propagation via query param for resumable streams |
| **Event callbacks** | Trigger HTTP calls (POST/PUT/PATCH) when specific events arrive — with retry |
| **Lifecycle hooks** | Observe connect, open, message, error, reconnect, and close events |
| **Global defaults** | Configure once via DI, override per-stream |
| **Credential support** | `withCredentials` for cookie-based auth with CORS |
| **Clean teardown** | Unsubscribe closes `EventSource` and removes all listeners automatically |

---

## ⚙️ Configuration

### Global defaults via DI

Use `provideSseClient()` in your `app.config.ts`:

```typescript
import { ApplicationConfig } from '@angular/core';
import { provideSseClient } from '@spectrayan/ng-sse-client';

export const appConfig: ApplicationConfig = {
  providers: [
    provideSseClient({
      withCredentials: true,
      lastEventIdParamName: 'lastEventId',
      reconnection: {
        enabled: true,
        maxRetries: -1,           // -1 = infinite
        initialDelayMs: 1000,
        maxDelayMs: 30000,
        backoffMultiplier: 2,
        jitterRatio: 0.2,
      },
    }),
  ],
};
```

Or provide the token directly:

```typescript
import { SSE_CLIENT_CONFIG } from '@spectrayan/ng-sse-client';

providers: [
  { provide: SSE_CLIENT_CONFIG, useValue: { withCredentials: true } },
]
```

> **Per-call options always override global defaults.**

### Per-call options

```typescript
sse.stream<any>('http://localhost:8080/sse/user', {
  withCredentials: true,
  events: ['notification'],        // named events (besides default "message")
  lastEventIdParamName: 'lastEventId',
  parse: (txt) => JSON.parse(txt),
  reconnection: {
    enabled: true,
    maxRetries: -1,
    initialDelayMs: 1000,
    maxDelayMs: 30000,
    backoffMultiplier: 2,
    jitterRatio: 0.2,
  },
  callbacks: [ /* see Event Callbacks section */ ],
  hooks: { /* see Lifecycle Hooks section */ },
});
```

---

## 📡 API Reference

### `SseClient`

| Method | Returns | Description |
|--------|---------|-------------|
| `stream<T>(url, options?)` | `Observable<T>` | Subscribes to default `message` events + any `options.events` |
| `streamEvent<T>(url, event, options?)` | `Observable<T>` | Subscribes to a single named event only |

Both return a **cold** `Observable` — the `EventSource` opens on subscribe and closes on unsubscribe.

### `StreamOptions<T>`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `parse` | `(data: string) => T` | `JSON.parse` | Custom parser for incoming payloads |
| `withCredentials` | `boolean` | `false` | Send cookies/auth headers with CORS |
| `events` | `string[]` | `[]` | Additional named events to listen for |
| `lastEventIdParamName` | `string` | `'lastEventId'` | Query param name for Last-Event-ID on reconnect |
| `reconnection` | `SseReconnectionConfig` | *(see below)* | Reconnection strategy |
| `callbacks` | `EventCallbackConfig[]` | `[]` | Event-driven HTTP callbacks |
| `hooks` | `SseClientHooks` | — | Lifecycle event observers |

---

## 🔄 Reconnection

The client uses exponential backoff with jitter for resilient reconnection:

```typescript
interface SseReconnectionConfig {
  enabled: boolean;          // Enable auto-reconnect
  maxRetries: number;        // -1 = infinite retries
  initialDelayMs: number;    // First retry delay (default: 1000)
  maxDelayMs: number;        // Max delay cap (default: 30000)
  backoffMultiplier: number; // Multiplier per attempt (default: 2)
  jitterRatio: number;       // Random variance ±% (default: 0.2)
}
```

**How it works:** Retry delay = `min(initialDelay × multiplier^attempt, maxDelay) × (1 ± jitterRatio)`

### Last-Event-ID

If the server includes `id:` fields in SSE frames, the client tracks the last received ID and appends `?lastEventId=<id>` to the URL on reconnect. This enables the server to replay missed events.

---

## 📞 Event Callbacks

Trigger HTTP calls automatically when events arrive — useful for acknowledgements, analytics, read receipts, etc.

```typescript
import { ApiCallbackConfig } from '@spectrayan/ng-sse-client';

const markReadCallback: ApiCallbackConfig<{ id: string }> = {
  method: 'POST',
  url: 'http://localhost:8080/api/notifications/mark-read',
  transformPayload: (event) => ({ notificationId: event.id }),
  headers: { 'Content-Type': 'application/json' },
  timeout: 5000,
};

sse.stream<any>('http://localhost:8080/sse/user', {
  events: ['notification'],
  callbacks: [{
    eventType: 'notification',
    condition: (data) => !!(data?.id),
    apiCallback: markReadCallback,
    retry: { enabled: true, maxRetries: 3, delayMs: 1000 },
  }],
}).subscribe(/* ... */);
```

| Property | Type | Description |
|----------|------|-------------|
| `eventType` | `string?` | Match specific event type (omit to apply to all) |
| `condition` | `(data: T) => boolean` | Guard — callback only fires if this returns `true` |
| `apiCallback.method` | `POST / PUT / PATCH` | HTTP method |
| `apiCallback.url` | `string` | Target URL |
| `apiCallback.transformPayload` | `(data: T) => any` | Transform event data into request body |
| `retry.enabled` | `boolean` | Retry failed HTTP calls |
| `retry.maxRetries` | `number` | Max retry attempts |
| `retry.delayMs` | `number` | Fixed delay between retries |

> **Callback failures are logged but never error the SSE stream.**

---

## 🔗 Lifecycle Hooks

Observe every stage of the connection lifecycle:

```typescript
sse.stream<any>('http://localhost:8080/sse/user', {
  hooks: {
    onConnect: (url) =>
      console.log('[SSE] Connecting to', url),
    onOpen: ({ url, attempt }) =>
      console.log('[SSE] Connected after', attempt, 'attempts'),
    onMessage: ({ eventType, data }) =>
      console.log('[SSE] Event:', eventType, data),
    onError: ({ willRetry, nextDelayMs }) =>
      console.warn('[SSE] Error, willRetry:', willRetry, 'in', nextDelayMs, 'ms'),
    onReconnectAttempt: ({ attempt, delayMs }) =>
      console.log('[SSE] Reconnecting, attempt', attempt, 'after', delayMs, 'ms'),
    onClose: ({ reason }) =>
      console.log('[SSE] Closed:', reason),
  },
});
```

| Hook | When | Payload |
|------|------|---------|
| `onConnect` | `EventSource` is about to be created | `url: string` |
| `onOpen` | Connection established | `{ url, attempt }` |
| `onMessage` | Event received (before parse) | `{ eventType, data, rawEvent }` |
| `onError` | Connection error | `{ event, attempt, willRetry, nextDelayMs? }` |
| `onReconnectAttempt` | About to retry | `{ attempt, delayMs }` |
| `onClose` | Stream closed | `{ reason: 'unsubscribe' \| 'complete' \| 'retriesExceeded' }` |

Hooks can be set globally via DI or per-stream. Per-stream hooks override global hooks.

---

## 🌍 Real-World Example

From the [sample app](../../samples/ng-sse-client-app/src/app/app.ts):

```typescript
import { ApiCallbackConfig, SseClient } from '@spectrayan/ng-sse-client';

const markReadCallback: ApiCallbackConfig<{ id: string }> = {
  method: 'POST',
  url: 'http://localhost:8080/api/notifications/mark-read',
  transformPayload: (event) => ({ notificationId: event.id }),
  headers: { 'Content-Type': 'application/json' },
  timeout: 5000,
};

sse.stream<any>(`http://localhost:8080/sse/john`, {
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
    condition: (d: any) => !!(d && typeof d === 'object' && 'id' in d),
    apiCallback: markReadCallback,
    retry: { enabled: true, maxRetries: 3, delayMs: 1000 },
  }],
}).subscribe(event => {
  // Update UI with incoming events
});
```

---

## 🧩 Advanced Topics

### Custom EventSource implementation

For polyfills, testing, or non-browser environments, implement `EventSourceLike` and provide a custom `EventSourceFactory` via DI:

```typescript
import { EventSourceFactory, EVENT_SOURCE_FACTORY } from '@spectrayan/ng-sse-client';

const customFactory: EventSourceFactory = (url, init) => {
  return new CustomEventSource(url, init);
};

providers: [
  { provide: EVENT_SOURCE_FACTORY, useValue: customFactory },
]
```

### Error handling

| Scenario | Behavior |
|----------|----------|
| Parser error | `Observable` emits `error` |
| Network error (reconnect enabled) | Automatic reconnection with backoff |
| Network error (reconnect disabled) | `Observable` errors and completes |
| Callback HTTP error | Logged, SSE stream continues unaffected |

### SSR / Angular Universal

SSE requires a browser `EventSource` API. In server-side rendering:
- Guard stream creation with `isPlatformBrowser()`
- Or inject a no-op `EventSourceFactory` on the server

### CORS & credentials

When your SSE endpoint requires cookie-based auth:
1. Set `withCredentials: true` in options or global config
2. Ensure the server sends `Access-Control-Allow-Origin` (not `*` when credentials are used)
3. Ensure the server sends `Access-Control-Allow-Credentials: true`

---

## 🎮 Sample Projects

| Sample | Description | Key File |
|--------|-------------|----------|
| [`ng-sse-client-app`](../../samples/ng-sse-client-app/) | Angular app with multiple users, notifications, reconnection, and callbacks | [`app.ts`](../../samples/ng-sse-client-app/src/app/app.ts) |
| [`sse-sample-server-app`](../../samples/sse-sample-server-app/) | Spring Boot server emitting `message` and `notification` events | — |

---

## 🏗️ Development

```bash
# Build the library
npx nx build ng-sse-client

# Run tests
npx nx test ng-sse-client --ci --codeCoverage=false
```

## 📄 License

[Apache License 2.0](../../LICENSE)

## 💬 Support

Questions or issues: **support@spectrayan.com**
