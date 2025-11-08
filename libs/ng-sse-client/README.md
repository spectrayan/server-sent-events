# @spectrayan-sse/ng-sse-client

A small, typed, zone-aware Server‑Sent Events (SSE) client for Angular.

- Typed APIs: `stream<T>(url)` and `streamEvent<T>(url, event)`
- Auto‑reconnect with exponential backoff + jitter
- Handles default `message` and custom named events
- Adds `?lastEventId=...` on reconnect to resume from the last received event
- Zone-friendly: network/parsing outside Angular, emissions re-enter the zone
- Optional event-driven API callbacks with retry (e.g., mark notifications as read)

---

## Install
```bash
npm install @spectrayan-sse/ng-sse-client
```

Peer dependencies
- Angular >=16 and <21 (incl. 20)
- RxJS 7

---

## Quick start
```ts
import { Component } from '@angular/core';
import { SseClient } from '@spectrayan-sse/ng-sse-client';

@Component({
  selector: 'app-demo',
  template: `<ul><li *ngFor="let m of messages">{{ m }}</li></ul>`
})
export class DemoComponent {
  messages: string[] = [];
  constructor(sse: SseClient) {
    sse.stream<string>('http://localhost:8080/sse/general').subscribe(m => {
      this.messages.unshift(m);
    });
  }
}
```

Named events
```ts
sse
  .streamEvent<{ id: string; text: string }>('http://localhost:8080/sse/notifications', 'notification')
  .subscribe(n => console.log(n.id, n.text));
```

Typed parsing (safe JSON-or-string)
```ts
const parse = <T>(txt: string): T => {
  try { return JSON.parse(txt) as T; } catch { return txt as unknown as T; }
};

sse.stream<any>('http://localhost:8080/sse/general', { parse }).subscribe(v => console.log(v));
```

---

## Real-world example (from the sample app)
See `samples/ng-sse-client-app/src/app/app.ts` for a complete demo. Summarized usage:

```ts
import { ApiCallbackConfig, SseClient } from '@spectrayan-sse/ng-sse-client';

// Configure a server-side side-effect to run when a notification arrives
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
  reconnection: { enabled: true, maxRetries: -1, initialDelayMs: 1000, maxDelayMs: 30000, backoffMultiplier: 2, jitterRatio: 0.2 },
  callbacks: [
    {
      eventType: 'notification',
      condition: (d: any) => !!(d && typeof d === 'object' && 'id' in d),
      apiCallback: markReadCallback,
      retry: { enabled: true, maxRetries: 3, delayMs: 1000 },
    },
  ],
}).subscribe(/* ... */);
```

- The client collects `message` and `notification` events, parses payloads, and updates the UI.
- When a `notification` arrives, it triggers a POST to mark it as read. The callback can retry without failing the SSE stream.

---

## Per-call options
```ts
sse.stream<any>('http://localhost:8080/sse/user', {
  withCredentials: true,
  events: ['notification'],      // named events ("message" is implicit and handled by default)
  lastEventIdParamName: 'lastEventId',
  parse: (txt) => { try { return JSON.parse(txt); } catch { return txt; } },
  reconnection: {
    enabled: true,
    maxRetries: -1,             // -1 = infinite retries
    initialDelayMs: 1000,
    maxDelayMs: 30000,
    backoffMultiplier: 2,
    jitterRatio: 0.2,
  },
  callbacks: [ /* see Event callbacks section */ ],
});
```

## Global defaults via DI
```ts
import { ApplicationConfig } from '@angular/core';
import { SSE_CLIENT_CONFIG } from '@spectrayan-sse/ng-sse-client';

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: SSE_CLIENT_CONFIG, useValue: {
      withCredentials: true,
      lastEventIdParamName: 'lastEventId',
      reconnection: { enabled: true, maxRetries: -1, initialDelayMs: 1000, maxDelayMs: 30000, backoffMultiplier: 2, jitterRatio: 0.2 },
    }},
  ],
};
```

Values passed to `stream()`/`streamEvent()` override global defaults.

---

## API reference

### SseClient
- `stream<T>(url: string, options?: StreamOptions<T>): Observable<T>`
  - Subscribes to default `message` events plus any `options.events` (excluding duplicate `message`).
- `streamEvent<T>(url: string, event: string, options?: StreamOptions<T>): Observable<T>`
  - Subscribes only to the given named event.

Both return a cold `Observable` that opens an `EventSource` connection on subscribe and closes it on unsubscribe/complete/error.

### StreamOptions<T>
`StreamOptions<T>` extends `Partial<Omit<SseClientConfig, 'url' | 'parse'>>` and adds:
- `parse?: (data: string) => T` — parser for incoming payload. Defaults to `JSON.parse` (see `DEFAULT_SSE_CLIENT_CONFIG`).

Practical fields you will commonly use:
- `withCredentials?: boolean`
- `events?: string[]` — named events to attach listeners for (besides default `message`).
- `lastEventIdParamName?: string` — query param name for lastEventId on reconnect.
- `reconnection?: SseReconnectionConfig`
- `callbacks?: EventCallbackConfig[]` — see next section.

### Event callbacks (optional)
You can trigger HTTP calls when events arrive — useful for acknowledgement flows, analytics, etc.

Types:
```ts
export interface ApiCallbackConfig<T = any> {
  method: 'POST' | 'PUT' | 'PATCH';
  url: string;
  transformPayload?: (eventData: T) => any;
  headers?: Record<string, string>;
  withCredentials?: boolean;
  timeout?: number; // ms
}

export interface EventCallbackConfig<T = any> {
  eventType?: string; // if omitted, applies to all events
  condition?: (eventData: T) => boolean;
  apiCallback: ApiCallbackConfig<T>;
  retry?: { enabled: boolean; maxRetries: number; delayMs: number };
}
```

Behavior:
- For each event, the client checks all `callbacks`.
- If `eventType` is set, it must match the current event.
- If `condition` is provided, it must return true.
- The HTTP request is executed via `ApiCallbackService` outside Angular zone.
- If `retry.enabled`, failed requests will be retried up to `maxRetries` with fixed delay `delayMs`.
- Callback failures are logged and do not error the SSE stream.

### Reconnection strategy
`SseReconnectionConfig` controls exponential backoff and jitter used when the underlying `EventSource` errors:
```ts
export interface SseReconnectionConfig {
  enabled: boolean;
  maxRetries: number;        // -1 = infinite
  initialDelayMs: number;    // default 1000
  maxDelayMs: number;        // default 30000
  backoffMultiplier: number; // default 2
  jitterRatio: number;       // default 0.2 (randomize +-20%)
}
```
The delay for retry N is computed with `computeBackoffDelay()` then bounded by `maxDelayMs` and randomized by `jitterRatio`.

### Last-event ID
If the server supports Last-Event-ID, the client tracks the last received event id (from `MessageEvent.lastEventId`) and appends `?lastEventId=<id>` to the URL on reconnect. Use `lastEventIdParamName` to change the parameter name.

### Cleanup
Unsubscribe to close the `EventSource` and remove all listeners. The library sets up teardown logic so there are no dangling connections.

---

## Advanced topics

### Custom EventSource implementation
If you need to provide a custom `EventSource` (for polyfills, testing, or environments), implement `EventSourceLike` and provide a custom `EventSourceFactory` via DI. See:
- `libs/ng-sse-client/src/lib/event-source.factory.ts`
- `libs/ng-sse-client/src/index.ts` exports

### Error handling
- Parser errors surface as `error` on the returned `Observable`.
- Network errors trigger the reconnection flow if enabled; otherwise the `Observable` errors and completes.
- Callback HTTP errors are logged and do not fail the SSE stream.

### SSR and environments
SSE requires a browser-like environment. On the server (Angular Universal), avoid creating `EventSource`. Guard code paths or inject a factory that no-ops on the server.

### CORS and credentials
- If your SSE endpoint requires cookies or auth headers managed by the browser, set `withCredentials: true` in options or global config.
- Ensure the server sets appropriate CORS headers for EventSource (e.g., `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials`).

---

## Sample projects
- Angular sample app (consumer): `samples/ng-sse-client-app` — demonstrates multiple users, notifications, reconnection, and callbacks. Key file: `samples/ng-sse-client-app/src/app/app.ts`.
- Spring WebFlux sample server: `samples/sse-sample-server-app` — emits `message` and `notification` events.

---

## Development
Build the library
```bash
npx nx build ng-sse-client
```

Run tests
```bash
npx nx test ng-sse-client --ci --codeCoverage=false
```

---

## License
Apache-2.0

## Support
support@spectrayan.com
