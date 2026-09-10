<div align="center">

# ⚡ @spectrayan/sse-client

**Enterprise-grade, type-safe, framework-agnostic Server-Sent Events client for Browser and Node.js**

[![npm](https://img.shields.io/npm/v/@spectrayan/sse-client?color=CB3837&logo=npm)](https://www.npmjs.com/package/@spectrayan/sse-client)
[![npm downloads](https://img.shields.io/npm/dm/@spectrayan/sse-client?color=blue)](https://www.npmjs.com/package/@spectrayan/sse-client)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0+-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://github.com/spectrayan/server-sent-events/blob/main/LICENSE)

Zero dependencies · Web Streams API · Reconnection with full jitter · Async Iterable · Dual ESM/CJS

</div>

---

## 💡 Why `@spectrayan/sse-client`?

The native browser `EventSource` API is limited: it lacks custom headers support (cannot pass Authorization headers without URL query hacks), offers no control over exponential backoff or jitter, and only works in browser environments.

`@spectrayan/sse-client` provides a universal, modern alternative:

| Capability | Browser `EventSource` | `@spectrayan/sse-client` |
|:---|:---|:---|
| **Runtime Environment** | Browser only | Universal (Browser, Node.js 18+, Bun, Deno, Edge Workers) |
| **Custom Headers** | ❌ No custom headers | ✅ Full header support (Authorization tokens, API keys, tracing) |
| **Async Iteration** | ❌ Event listener only | ✅ Native `for await (const event of client.iterate(...))` |
| **Reconnection Strategy** | ⚠️ Fixed browser retry | ✅ Exponential backoff, configurable max delay, and random jitter |
| **Heartbeat Filtering** | ⚠️ Emits raw comments | ✅ Discards `:keepalive` and comment frames per spec |
| **Last-Event-ID** | ⚠️ Browser-dependent | ✅ Automatic tracking, header injection, and query fallback |
| **Type Safety** | ❌ Raw string payloads | ✅ Strongly typed generics with built-in JSON deserialization |

---

## 📦 Installation

```bash
npm install @spectrayan/sse-client
```

---

## 🚀 Quick Start

### 1. Modern Async Iteration (`for await`)

```typescript
import { createSseClient } from '@spectrayan/sse-client';

const client = createSseClient({
  headers: {
    Authorization: 'Bearer my-auth-token',
  },
});

interface OrderEvent {
  orderId: string;
  status: string;
}

async function watchOrders() {
  for await (const order of client.iterate<OrderEvent>('https://api.example.com/sse/orders')) {
    console.log('Order status updated:', order.orderId, order.status);
  }
}
```

### 2. Observer Subscription Pattern

```typescript
import { createSseClient } from '@spectrayan/sse-client';

const client = createSseClient();

const subscription = client.stream<string>('https://api.example.com/sse/ticks', {
  reconnection: {
    initialDelayMs: 1500,
    maxDelayMs: 60000,
    backoffMultiplier: 2.0,
    jitterRatio: 0.25,
  },
  onOpen: ({ url, attempt }) => console.log('Connected to', url),
  onError: ({ error, willRetry, nextDelayMs }) => console.warn('Stream interrupted:', error.message),
}, {
  next: (data) => console.log('Received payload:', data),
  error: (err) => console.error('Stream failed permanently:', err),
  complete: () => console.log('Stream completed cleanly'),
});

// To disconnect at any time:
subscription.unsubscribe();
```

### 3. Named Event Demultiplexing

```typescript
client.streamEvent<Notification>('https://api.example.com/sse/feed', 'user_notification', {}, {
  next: (notification) => alert(notification.title),
});
```

---

## 🔄 Reconnection & Jitter

Automatic reconnection calculates delay using bounded exponential backoff with full jitter:

$$\text{Delay} = \min(\text{initialDelay} \times \text{backoffMultiplier}^{\text{attempt}}, \text{maxDelay}) \times (1 \pm \text{jitterRatio})$$

```typescript
const client = createSseClient({
  reconnection: {
    enabled: true,
    maxRetries: -1,         // -1 = infinite
    initialDelayMs: 1000,   // Start at 1s
    maxDelayMs: 30000,      // Cap at 30s
    backoffMultiplier: 2.0, // Double delay each attempt
    jitterRatio: 0.2,       // +/- 20% random spread
  },
});
```

---

## 📡 API Reference

### `createSseClient(config?: SseClientConfig): SseClient`

| Config Option | Type | Default | Description |
|:---|:---|:---|:---|
| `baseUrl` | `string` | `undefined` | Prefix applied to relative stream URLs. |
| `headers` | `Record<string, string> \| (() => Promise<Record<string, string>>)` | `undefined` | Static headers or async provider function. |
| `withCredentials` | `boolean` | `false` | Includes cookies on cross-origin requests. |
| `lastEventIdParamName` | `string` | `'lastEventId'` | Query parameter key used on reconnect resumption. |
| `reconnection` | `Partial<SseReconnectionConfig>` | *(defaults)* | Global retry policy. |
| `fetch` | `typeof fetch` | `globalThis.fetch` | Custom fetch polyfill if needed. |

---

## 📄 License

Distributed under the **Apache License 2.0**. See [LICENSE](https://github.com/spectrayan/server-sent-events/blob/main/LICENSE) for details.
