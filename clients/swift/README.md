# Spectrayan Swift SSE Client (`SpectrayanSSE`)

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Swift](https://img.shields.io/badge/Swift-6.0-orange.svg)](https://swift.org)
[![Platforms](https://img.shields.io/badge/Platforms-iOS%20%7C%20macOS%20%7C%20watchOS%20%7C%20tvOS%20%7C%20visionOS-lightgrey.svg)](#requirements)

Lightweight Swift client for consuming Server-Sent Events (SSE / W3C EventSource) on Apple platforms. Built on Swift Concurrency (`AsyncThrowingStream<ServerSentEvent, Error>`) and `URLSession`, with automatic reconnection, randomized exponential backoff, and `Last-Event-ID` resumption.

Part of the [Spectrayan Server-Sent Events](https://github.com/spectrayan/server-sent-events) polyglot client ecosystem.

---

## Features

- **Swift Concurrency**: Consume events with `for try await`; types are `Sendable` and build cleanly under Swift 6 strict concurrency.
- **Cancellation-Aware**: Cancelling the consuming task (or leaving the loop, or a SwiftUI `.task` ending) cancels the in-flight request and stops reconnecting.
- **W3C Standard Compliance**: Byte-level streaming parser supporting multi-line `data:`, custom `event:` names, `id:` tracking, `retry:` delays, `:keepalive` comments, CR / LF / CRLF line endings, and a leading UTF-8 BOM.
- **Resilient Reconnection**: Reconnects after network drops, timeouts, and 408 / 429 / 5xx responses, sending `Last-Event-ID` so the server can resume. HTTP 204 stops the stream; other 4xx responses and non-`text/event-stream` responses fail it.
- **Randomized Exponential Backoff**: Full jitter avoids thundering herds; the server's `retry:` value replaces the base delay:
  $$\text{delay} = \text{random}\big(0,\ \min(\text{initialDelay} \times \text{multiplier}^{\text{attempt}},\ \text{maxDelay})\big)$$
- **Zero External Dependencies**: Implemented with Foundation (`URLSession`, `URLSessionDataDelegate`) only.

---

## Requirements

- Swift 6.0+ (Swift Package Manager)
- iOS 15+, macOS 12+, watchOS 8+, tvOS 15+, visionOS 1+

---

## Installation

### Swift Package Manager

The package manifest lives in `clients/swift/`. Swift Package Manager resolves remote packages from a repository's root, so add a checkout of this repository as a local package:

```swift
// Package.swift
dependencies: [
    .package(path: "path/to/server-sent-events/clients/swift")
],
targets: [
    .target(
        name: "MyApp",
        dependencies: [.product(name: "SpectrayanSSE", package: "swift")]
    )
]
```

In Xcode, use **File → Add Package Dependencies… → Add Local…** and select `clients/swift`.

---

## Quick Start

```swift
import Foundation
import SpectrayanSSE

let client = SSEClient(
    url: URL(string: "https://example.com/events")!
)

for try await event in client.events() {
    print(event.data)
}
```

Each `ServerSentEvent` exposes `id`, `event` (defaults to `"message"`), `data`, `retryMilliseconds`, and the `comments` received before it.

### SwiftUI

```swift
.task {
    do {
        for try await event in client.events() {
            messages.append(event.data)
        }
    } catch {
        self.error = error
    }
}
```

The connection closes automatically when the view disappears.

---

## Reconnection Settings (`ReconnectionPolicy`)

```swift
let client = SSEClient(
    url: URL(string: "https://example.com/events")!,
    session: .shared,                    // any URLSession
    reconnectionPolicy: ReconnectionPolicy(
        initialDelayMilliseconds: 1_000, // base delay for the first reconnect
        maxDelayMilliseconds: 30_000,    // upper bound for any single delay
        multiplier: 2.0,                 // exponential growth factor
        maxRetries: nil                  // nil = unlimited, 0 = never reconnect
    )
)
```

On iOS 16 / macOS 13 and later, `ReconnectionPolicy(initialDelay: .seconds(1), maxDelay: .seconds(30))` accepts `Duration` values.

When retries run out, the stream throws the last error (`URLError` or `SSEClientError`).

---

## Running Tests

```bash
cd clients/swift
swift test
```

---

## License

Apache License 2.0. See [LICENSE](../../LICENSE) for details.
