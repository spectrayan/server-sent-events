# spectrayan-sse-client

[![PyPI version](https://img.shields.io/pypi/v/spectrayan-sse-client.svg)](https://pypi.org/project/spectrayan-sse-client/)
[![Python versions](https://img.shields.io/pypi/pyversions/spectrayan-sse-client.svg)](https://pypi.org/project/spectrayan-sse-client/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Modern, resilient, asynchronous **Server-Sent Events (SSE)** client for Python built on top of **httpx** and **Pydantic V2**.

Designed for high-throughput streaming, AI agent outputs (OpenAI, Anthropic, Ollama, Spector), FastAPI microservices, and event-driven data pipelines.

---

## Features

- ⚡ **Asynchronous & Fast**: Built natively on `httpx` async streams with HTTP/2 and connection pooling.
- 🛡️ **Pydantic V2 Validation**: Directly parse streaming JSON event payloads into strongly-typed Pydantic models.
- 🔁 **Automatic Reconnection**: Self-healing streams with configurable exponential backoff and randomized jitter.
- 🎯 **Last-Event-ID State Tracking**: Seamlessly resumes broken streams without dropped messages by persisting the latest event ID.
- 🔇 **Heartbeat Filtering**: Automatically filters and ignores `:keepalive` and ping comment frames according to the W3C SSE standard.
- 🧵 **Synchronous Adapter**: Includes `SyncSseClient` for legacy synchronous architectures and scripts.

---

## Installation

```bash
pip install spectrayan-sse-client
```

---

## Quick Start

### Basic Async Stream

```python
import asyncio
from spectrayan_sse import SseClient

async def main():
    async with SseClient("https://api.example.com/sse/stream") as client:
        async for event in client.stream():
            print(f"[{event.event}] (id={event.id}): {event.data}")

if __name__ == "__main__":
    asyncio.run(main())
```

---

### Typed Pydantic V2 Event Streaming

Stream directly into validated Pydantic models:

```python
import asyncio
from pydantic import BaseModel
from spectrayan_sse import SseClient

class MarketTick(BaseModel):
    symbol: str
    price: float
    volume: int

async def main():
    url = "https://api.example.com/sse/market"
    async with SseClient(url) as client:
        async for tick in client.stream_events(MarketTick, event_type="tick"):
            print(f"Symbol: {tick.symbol} | Price: ${tick.price:,.2f} | Volume: {tick.volume}")

if __name__ == "__main__":
    asyncio.run(main())
```

---

### Custom Authentication & Headers

Pass arbitrary HTTP headers, authorization tokens, or query parameters:

```python
async with SseClient(
    "https://api.example.com/sse/secure-feed",
    headers={
        "Authorization": "Bearer your-secret-api-token",
        "X-Organization-ID": "org-987",
    },
) as client:
    async for event in client.stream():
        print(event.data)
```

---

### Custom Reconnection & Backoff

Control retry intervals, maximum attempts, and backoff jitter:

```python
from spectrayan_sse import SseClient, SseReconnectionConfig

config = SseReconnectionConfig(
    enabled=True,
    initial_delay_ms=500,     # Start reconnecting at 500ms
    max_delay_ms=15000,       # Cap retry delay at 15s
    multiplier=1.5,           # Multiply delay by 1.5 per attempt
    jitter=0.2,               # Apply ±20% randomized jitter
    max_retries=10,           # Max 10 attempts before raising SseConnectionError
)

async with SseClient("https://api.example.com/sse/events", reconnection=config) as client:
    async for event in client.stream():
        print(event.data)
```

The backoff formula computes delay as:
$$\text{delay} = \min(\text{initial} \times \text{multiplier}^{\text{attempt}}, \text{max}) \times (1 \pm \text{jitter})$$

---

### Synchronous Client (`SyncSseClient`)

For non-async environments or worker scripts:

```python
from spectrayan_sse import SyncSseClient

with SyncSseClient("https://api.example.com/sse/events") as client:
    for event in client.stream():
        print(f"Received: {event.data}")
```

---

## API Reference

### `SseClient`
- `__init__(url, headers=None, reconnection=None, client=None, timeout=60.0)`
- `stream() -> AsyncGenerator[SseEvent, None]`
- `stream_events(model_cls: Type[T], event_type: Optional[str] = None) -> AsyncGenerator[T, None]`
- `aclose() -> None`

### `SseEvent`
- `id: Optional[str]`: Event ID
- `event: str`: Event name (defaults to `"message"`)
- `data: str`: Event payload
- `retry: Optional[int]`: Reconnection time in ms
- `json_data() -> Any`: Parses `data` as JSON
- `parse_as(model_cls: Type[T]) -> T`: Validates `data` into Pydantic model

### `SseReconnectionConfig`
- `enabled: bool = True`
- `initial_delay_ms: int = 1000`
- `max_delay_ms: int = 30000`
- `multiplier: float = 1.5`
- `jitter: float = 0.2`
- `max_retries: Optional[int] = None`

---

## License

Licensed under the Apache License, Version 2.0.