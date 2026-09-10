<div align="center">

# ⚛️ @spectrayan/react-sse

**Idiomatic, production-ready React hooks for Server-Sent Events (SSE)**

[![npm](https://img.shields.io/npm/v/@spectrayan/react-sse?color=CB3837&logo=npm)](https://www.npmjs.com/package/@spectrayan/react-sse)
[![npm downloads](https://img.shields.io/npm/dm/@spectrayan/react-sse?color=blue)](https://www.npmjs.com/package/@spectrayan/react-sse)
[![React](https://img.shields.io/badge/React-18_&_19-61DAFB?logo=react&logoColor=black)](https://react.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0+-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://github.com/spectrayan/server-sent-events/blob/main/LICENSE)

Automatic lifecycle cleanup · Reconnect with jitter · Named events · Context pooling · SSR-safe

</div>

---

## 📦 Installation

```bash
npm install @spectrayan/react-sse @spectrayan/sse-client
```

### Peer Dependencies
- `react`: `>=18.0.0` (Supports React 18 & 19)
- `@spectrayan/sse-client`: `^2.0.0`

---

## 🚀 Quick Start

### 1. Basic Stream with `useSseStream`

```tsx
import React from 'react';
import { useSseStream } from '@spectrayan/react-sse';

interface Notification {
  id: string;
  message: string;
}

export function NotificationWidget() {
  const { data, status, error, reconnect, close } = useSseStream<Notification>(
    'https://api.example.com/sse/notifications',
    {
      reconnection: { initialDelayMs: 1000, maxDelayMs: 15000 },
    }
  );

  if (status === 'connecting') return <div>Connecting to live updates...</div>;
  if (status === 'error') return <div>Error: {error?.message} <button onClick={reconnect}>Retry</button></div>;

  return (
    <div>
      <h3>Live Alerts ({status})</h3>
      {data && <p>{data.message}</p>}
      <button onClick={close}>Disconnect</button>
    </div>
  );
}
```

### 2. Filtering Named Events with `useSseEvent`

When your SSE backend emits multiple named events on the same endpoint (e.g. `event: trade`, `event: heartbeat`):

```tsx
import { useSseEvent } from '@spectrayan/react-sse';

export function TradeTicker() {
  const { data: trade } = useSseEvent<Trade>(
    'https://api.example.com/sse/market',
    'trade'
  );

  return <div>Latest Trade: {trade?.symbol} @ ${trade?.price}</div>;
}
```

### 3. Shared Context with `SseProvider`

Configure authentication tokens or global reconnection once at the top of your component tree:

```tsx
import { SseProvider } from '@spectrayan/react-sse';

export function App() {
  return (
    <SseProvider
      config={{
        baseUrl: 'https://api.example.com',
        headers: async () => ({
          Authorization: `Bearer ${await getAuthToken()}`,
        }),
        reconnection: { jitterRatio: 0.25 },
      }}
    >
      <MainDashboard />
    </SseProvider>
  );
}
```

---

## 📡 API Reference

### `useSseStream<T>(url, options?)`

Returns:
- `data: T | undefined` — The latest parsed event data.
- `status: 'idle' | 'connecting' | 'open' | 'error' | 'closed'` — Current connection state.
- `error: Error | null` — Connection or parsing error if occurred.
- `reconnect: () => void` — Manually forces a reconnection attempt.
- `close: () => void` — Explicitly closes the stream.

Options:
- `enabled?: boolean` (default: `true`) — Set to `false` to defer connecting.
- `initialData?: T` — Initial state value before the first event arrives.
- `reconnection?: Partial<SseReconnectionConfig>` — Override backoff multiplier, jitter, and delay caps.
- `headers?: Record<string, string>` — Custom request headers.

---

## 📄 License

Distributed under the **Apache License 2.0**. See [LICENSE](https://github.com/spectrayan/server-sent-events/blob/main/LICENSE) for details.
