<div align="center">

# 📱 @spectrayan/ng-sse-client

**Enterprise-grade, typed, zone-aware Server-Sent Events client for Angular**

[![npm](https://img.shields.io/npm/v/@spectrayan/ng-sse-client?color=CB3837&logo=npm)](https://www.npmjs.com/package/@spectrayan/ng-sse-client)
[![npm downloads](https://img.shields.io/npm/dm/@spectrayan/ng-sse-client?color=blue)](https://www.npmjs.com/package/@spectrayan/ng-sse-client)
[![Angular](https://img.shields.io/badge/Angular-16_to_22-DD0031?logo=angular&logoColor=white)](https://angular.dev)
[![RxJS](https://img.shields.io/badge/RxJS-7+-B7178C?logo=reactivex&logoColor=white)](https://rxjs.dev)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://github.com/spectrayan/server-sent-events/blob/main/LICENSE)

Type-safe SSE streams with automatic reconnection, exponential backoff,
zone-optimized performance, and optional event-driven callbacks.

</div>

---

## 💡 Why `@spectrayan/ng-sse-client`?

The native browser `EventSource` API is limited: it does not support custom headers or JSON deserialization, reconnection cannot be customized, and unmanaged listeners trigger runaway Angular change detection cycles.

`@spectrayan/ng-sse-client` solves these problems with an idiomatic, production-hardened Angular design:

| Capability | Browser `EventSource` | `@spectrayan/ng-sse-client` |
|:---|:---|:---|
| **Type Safety** | ❌ Untyped string payloads | ✅ Strongly-typed generic streams (`stream<T>`, `streamEvent<T>`) |
| **Angular Zone Optimization** | ❌ Triggers full CD on every raw packet | ✅ Network I/O runs outside `NgZone`; re-enters only on dispatch |
| **Angular Signals** | ❌ Requires manual bridging | ✅ Drop-in reactive signals with Angular's `toSignal()` |
| **Reconnection Strategy** | ⚠️ Fixed browser retry (often 3s) | ✅ Exponential backoff, jitter, retry caps, and delay limits |
| **Stream Resumption** | ⚠️ Browser-dependent `Last-Event-ID` | ✅ Explicit query parameter and header `Last-Event-ID` propagation |
| **Named Event Demuxing** | ❌ Separate listeners required | ✅ Filter and listen to specific named SSE events directly |
| **Event Callbacks** | ❌ Manual boilerplate | ✅ Built-in HTTP acknowledgement/webhook triggers with retry |
| **Lifecycle Observability** | ⚠️ Limited `onerror` / `onmessage` | ✅ 6 granular lifecycle hooks (`onConnect`, `onOpen`, `onMessage`, etc.) |

---

## 📦 Installation

```bash
npm install @spectrayan/ng-sse-client
```

### Peer Dependencies Compatibility

| Package | Version Range |
|:---|:---|
| `@angular/core` | `>=16.0.0 <23.0.0` (Supports Angular 16, 17, 18, 19, 20, 21, 22) |
| `@angular/common` | `>=16.0.0 <23.0.0` |
| `rxjs` | `>=7.0.0 <8.0.0` |

---

## 🚀 Quick Start

### 1. Global Setup via Dependency Injection

In modern Angular applications (Angular 16+), configure the provider in `app.config.ts`:

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
        maxRetries: -1, // infinite retries
        initialDelayMs: 1000,
        maxDelayMs: 30000,
        backoffMultiplier: 2,
        jitterRatio: 0.2,
      },
    }),
  ],
};
```

*For NgModule-based architectures, provide `SSE_CLIENT_CONFIG`:*
```typescript
import { SSE_CLIENT_CONFIG } from '@spectrayan/ng-sse-client';

@NgModule({
  providers: [
    { provide: SSE_CLIENT_CONFIG, useValue: { withCredentials: true } },
  ],
})
export class AppModule {}
```

---

### 2. Consuming Streams in Components

#### Option A: Standalone Component with Angular Signals (`toSignal`)

```typescript
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { SseClient } from '@spectrayan/ng-sse-client';

interface LivePrice {
  symbol: string;
  price: number;
}

@Component({
  selector: 'app-ticker',
  standalone: true,
  template: `
    @if (latestPrice(); as ticker) {
      <div class="ticker">{{ ticker.symbol }}: \${{ ticker.price }}</div>
    } @else {
      <p>Connecting to live market stream...</p>
    }
  `,
})
export class TickerComponent {
  private sse = inject(SseClient);

  readonly latestPrice = toSignal(
    this.sse.stream<LivePrice>('https://api.example.com/sse/prices')
  );
}
```

#### Option B: Observable Stream with `async` Pipe

```typescript
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { SseClient } from '@spectrayan/ng-sse-client';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2>Live Notifications</h2>
    <ul>
      <li *ngFor="let notification of notifications$ | async">
        {{ notification }}
      </li>
    </ul>
  `,
})
export class NotificationsComponent {
  notifications$: Observable<string>;

  constructor(sse: SseClient) {
    this.notifications$ = sse.stream<string>('https://api.example.com/sse/notifications');
  }
}
```

#### Option C: Named Event Demultiplexing

When your SSE backend emits specific event channels (e.g. `event: user_joined`):

```typescript
interface UserEvent {
  userId: string;
  name: string;
}

sse.streamEvent<UserEvent>('https://api.example.com/sse/room-1', 'user_joined')
  .subscribe(user => console.log('New user joined:', user.name));
```

#### Option D: Custom Parsing

```typescript
sse.stream<CustomPayload>('https://api.example.com/sse/feed', {
  parse: (rawString) => {
    const json = JSON.parse(rawString);
    return new CustomPayload(json);
  },
}).subscribe(data => console.log(data));
```

---

## ✨ Core Capabilities

### 1. Zone-Optimized Performance
Native `EventSource` dispatches events on the browser main thread, triggering continuous Angular change detection cycles. `@spectrayan/ng-sse-client` processes connection negotiation, reconnect timers, and JSON parsing **completely outside `NgZone`**. The client only re-enters the Angular zone when emitting an actual payload to the subscriber.

### 2. Resilient Auto-Reconnection
Configured with exponential backoff and random jitter to protect backends from thundering herds during network interruptions:

$$\text{Delay} = \min(\text{initialDelay} \times \text{backoffMultiplier}^{\text{attempt}}, \text{maxDelay}) \times (1 \pm \text{jitterRatio})$$

```typescript
sse.stream('https://api.example.com/sse/events', {
  reconnection: {
    enabled: true,
    maxRetries: 5,           // Stop after 5 failed attempts
    initialDelayMs: 1500,     // Start with 1.5s
    maxDelayMs: 60000,        // Cap at 60s
    backoffMultiplier: 2.0,   // Double delay on each failure
    jitterRatio: 0.25,        // 25% random spread
  },
});
```

### 3. Resumable Streams via `Last-Event-ID`
When incoming SSE frames include an `id: <value>` line, the client automatically records the ID. On disconnect/reconnect, it appends `?lastEventId=<id>` (configurable via `lastEventIdParamName`) to resume without data loss.

### 4. Event-Driven API Callbacks (Acknowledgements & Webhooks)
Trigger HTTP operations automatically upon receiving specific event types — ideal for read receipts, delivery confirmations, or analytic tracking:

```typescript
import { ApiCallbackConfig } from '@spectrayan/ng-sse-client';

const ackCallback: ApiCallbackConfig<{ id: string }> = {
  method: 'POST',
  url: 'https://api.example.com/api/ack',
  transformPayload: (event) => ({ messageId: event.id, timestamp: Date.now() }),
  headers: { 'Content-Type': 'application/json' },
  timeout: 5000,
};

sse.stream('https://api.example.com/sse/events', {
  events: ['order_created'],
  callbacks: [{
    eventType: 'order_created',
    condition: (data) => !!data?.id,
    apiCallback: ackCallback,
    retry: { enabled: true, maxRetries: 3, delayMs: 1000 },
  }],
}).subscribe();
```
> *Callback execution failures are logged safely and will never disrupt or terminate the active SSE stream.*

### 5. Granular Lifecycle Hooks

```typescript
sse.stream('https://api.example.com/sse/live', {
  hooks: {
    onConnect: (url) => console.log('[SSE] Opening connection to', url),
    onOpen: ({ url, attempt }) => console.log('[SSE] Connected after attempt', attempt),
    onMessage: ({ eventType, data }) => console.log('[SSE] Received', eventType, data),
    onError: ({ willRetry, nextDelayMs }) => console.warn('[SSE] Error; willRetry:', willRetry, nextDelayMs),
    onReconnectAttempt: ({ attempt, delayMs }) => console.log('[SSE] Attempting retry #', attempt),
    onClose: ({ reason }) => console.log('[SSE] Disconnected:', reason),
  },
});
```

---

## 📡 API Reference

### `SseClient`

| Method | Return Type | Description |
|:---|:---|:---|
| `stream<T>(url: string, options?: StreamOptions<T>)` | `Observable<T>` | Opens a cold Observable listening to default `message` events and any additional named events declared in `options.events`. |
| `streamEvent<T>(url: string, event: string, options?: StreamOptions<T>)` | `Observable<T>` | Opens a cold Observable filtered exclusively to the specified named SSE event. |

### `StreamOptions<T>`

| Option | Type | Default | Description |
|:---|:---|:---|:---|
| `parse` | `(data: string) => T` | `JSON.parse` | Deserialization hook for incoming payloads. |
| `withCredentials` | `boolean` | `false` | Includes cookies and authorization headers with CORS requests. |
| `events` | `string[]` | `[]` | List of named SSE event strings to listen for. |
| `lastEventIdParamName` | `string` | `'lastEventId'` | URL query parameter key used when reconnecting. |
| `reconnection` | `SseReconnectionConfig` | *(standard defaults)* | Exponential backoff and retry policy. |
| `callbacks` | `EventCallbackConfig[]` | `[]` | Automatic HTTP dispatch rules triggered by events. |
| `hooks` | `SseClientHooks` | `undefined` | Observers for connection lifecycle events. |

---

## 🧩 Server-Side Rendering (SSR)

In Angular Universal / SSR environments where `EventSource` is not globally available in Node.js:
- Guard client stream initialization with `isPlatformBrowser(platformId)`:
```typescript
import { isPlatformBrowser } from '@angular/common';
import { PLATFORM_ID, inject } from '@angular/core';

export class LiveService {
  private platformId = inject(PLATFORM_ID);
  private sse = inject(SseClient);

  getStream() {
    if (isPlatformBrowser(this.platformId)) {
      return this.sse.stream('/api/sse');
    }
    return EMPTY;
  }
}
```
- Or provide a custom `EVENT_SOURCE_FACTORY` using an isomorphic polyfill (such as `eventsource`).

---

## 🎮 Sample Applications & Ecosystem

Check out the runnable sample projects in the GitHub repository:
- 📱 [Angular Sample Application (`ng-sse-client-app`)](https://github.com/spectrayan/server-sent-events/tree/main/samples/ng-sse-client-app): Full demo featuring real-time user notification channels, retry simulation, and callbacks.
- ⚡ [Spring Boot Backend Sample (`sse-sample-server-app`)](https://github.com/spectrayan/server-sent-events/tree/main/samples/sse-sample-server-app): High-throughput Spring WebFlux SSE backend.

---

## 📄 License

Distributed under the **Apache License 2.0**. See [LICENSE](https://github.com/spectrayan/server-sent-events/blob/main/LICENSE) for details.

## 💬 Community & Support

- 🐛 **Issues**: [GitHub Issue Tracker](https://github.com/spectrayan/server-sent-events/issues)
- 🏢 **Organization**: [Spectrayan GitHub](https://github.com/spectrayan)
- ✉️ **Contact**: [support@spectrayan.com](mailto:support@spectrayan.com)
