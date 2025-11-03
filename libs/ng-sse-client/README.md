# @spectrayan-sse/ng-sse-client

Angular SSE client library for consuming Server‑Sent Events (SSE).

- Typed APIs: `stream<T>(url)` and `streamEvent<T>(url, event)`
- Auto‑reconnect with exponential backoff and jitter
- Works with default `message` or named events
- Respects `lastEventId` on reconnect (`?lastEventId=...` by default)
- Zone-friendly: parsing/IO outside Angular zone, emissions re-enter the zone

## Installation
```
npm install @spectrayan-sse/ng-sse-client
```

Peer dependencies:
- Angular 16–20 (peer range: ">=16 <21 || ^20")
- RxJS 7

## Quick usage
```ts
import { Component } from '@angular/core';
import { SseClient } from '@spectrayan-sse/ng-sse-client';

@Component({
  selector: 'app-demo',
  template: `<ul><li *ngFor="let m of messages">{{ m }}</li></ul>`,
})
export class DemoComponent {
  messages: string[] = [];
  constructor(sse: SseClient) {
    sse.stream<string>('http://localhost:8080/notifications').subscribe((msg) => {
      this.messages.unshift(msg);
    });
  }
}
```

Subscribe to a specific named event:
```ts
sse.streamEvent<{ id: string; text: string }>('http://localhost:8080/notifications', 'notification').subscribe(n => {
  console.log(n.id, n.text);
});
```

## Configuration
You can pass options per call, or provide global defaults via Angular DI.

Per-call options:
```ts
sse.stream<any>('http://localhost:8080/notifications', {
  withCredentials: true,
  events: ['message', 'notification'],
  lastEventIdParamName: 'lastEventId',
  // Use a custom parser; defaults to JSON.parse
  parse: (txt) => {
    try { return JSON.parse(txt); } catch { return txt; }
  },
  reconnection: {
    enabled: true,
    maxRetries: -1,           // -1 = infinite
    initialDelayMs: 1000,
    maxDelayMs: 30000,
    backoffMultiplier: 2,
    jitterRatio: 0.2,
  },
});
```

Global defaults via provider:
```ts
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { SSE_CLIENT_CONFIG } from '@spectrayan-sse/ng-sse-client';

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: SSE_CLIENT_CONFIG, useValue: { withCredentials: true } },
  ],
};
```

## Development
Build:
```
npx nx build ng-sse-client
```

Test:
```
npx nx test ng-sse-client --ci --codeCoverage=false
```

## License
Apache-2.0

## Support
Questions or issues: support@spectrayan.com
