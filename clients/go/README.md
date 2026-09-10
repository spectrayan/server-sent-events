# Spectrayan Go SSE Client (`go-sse-client`)

[![Go Reference](https://pkg.go.dev/badge/github.com/spectrayan/server-sent-events/clients/go.svg)](https://pkg.go.dev/github.com/spectrayan/server-sent-events/clients/go)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Idiomatic, high-performance Go client for consuming Server-Sent Events (SSE / W3C EventSource) with channel-based streaming, `context.Context` cancellation, automatic reconnection with `Last-Event-ID` tracking, and jittered exponential backoff.

Part of the [Spectrayan Server-Sent Events](https://github.com/spectrayan/server-sent-events) polyglot client ecosystem.

---

## Features

- **Idiomatic Go Concurrency**: Stream events and error notifications via native Go channels (`<-chan Event` and `<-chan error`).
- **Context-Aware Lifecycle**: Clean, immediate shutdown when `context.Context` is cancelled without goroutine leaks.
- **W3C Standard Compliance**: Full SSE framing parser supporting multi-line `data:`, custom `event:` names, `id:` tracking, `retry:` delays, and `:keepalive` heartbeat comments.
- **Resilient Reconnection**: Automatically reconnects upon network drops, sending `Last-Event-ID` for seamless resumption.
- **Jittered Exponential Backoff**: Prevents thundering herd problems with full randomized jitter:
  $$\text{delay} = \min(\text{initial} \times \text{multiplier}^{\text{attempt}}, \text{max}) \times (1 \pm \text{jitter})$$
- **Zero External Dependencies**: Implemented strictly using the Go standard library (`net/http`, `bufio`, `time`, `context`).

---

## Installation

```bash
go get github.com/spectrayan/server-sent-events/clients/go
```

---

## Quick Start

```go
package main

import (
	"context"
	"fmt"
	"log"
	"time"

	sseclient "github.com/spectrayan/server-sent-events/clients/go"
)

type StockTick struct {
	Symbol string  `json:"symbol"`
	Price  float64 `json:"price"`
}

func main() {
	client := sseclient.New("https://api.example.com/sse/ticks",
		sseclient.WithHeader("Authorization", "Bearer my-token"),
		sseclient.WithReconnectionConfig(sseclient.ReconnectionConfig{
			InitialDelay: 1 * time.Second,
			MaxDelay:     30 * time.Second,
			Multiplier:   2.0,
			Jitter:       0.2, // ±20%
		}),
	)

	ctx, cancel := context.WithTimeout(context.Background(), 1*time.Minute)
	defer cancel()

	events, errs := client.Stream(ctx)

	for {
		select {
		case <-ctx.Done():
			log.Println("Context timed out, closing stream.")
			return

		case err, ok := <-errs:
			if !ok {
				return
			}
			log.Printf("Connection warning: %v", err)

		case event, ok := <-events:
			if !ok {
				log.Println("Stream ended by server.")
				return
			}

			var tick StockTick
			if err := event.JSON(&tick); err != nil {
				log.Printf("Raw event [%s]: %s", event.Type, event.String())
				continue
			}

			fmt.Printf("Tick %s: $%.2f (Event ID: %s)\n", tick.Symbol, tick.Price, event.ID)
		}
	}
}
```

---

## Event Filtering

To subscribe exclusively to a specific event type:

```go
// Only receives events where event.Type == "order_filled"
orders, errs := client.StreamEvents(ctx, "order_filled")

for order := range orders {
    fmt.Println("New order:", order.String())
}
```

---

## Configuration Options

| Option | Description | Default |
| :--- | :--- | :--- |
| `WithHTTPClient(*http.Client)` | Custom HTTP transport or TLS configuration | `&http.Client{Timeout: 0}` |
| `WithHeader(key, value)` | Single HTTP request header sent on each connect | None |
| `WithHeaders(map[string]string)` | Multiple HTTP request headers | None |
| `WithLastEventID(string)` | Pre-seed `Last-Event-ID` for resuming an earlier session | `""` |
| `WithBufferSize(int)` | Buffered channel capacity for events and errors | `100` |
| `WithReconnectionConfig(cfg)` | Custom backoff parameters | `DefaultReconnectionConfig()` |

### Reconnection Settings (`ReconnectionConfig`)

```go
type ReconnectionConfig struct {
    InitialDelay time.Duration // default: 1s
    MaxDelay     time.Duration // default: 30s
    Multiplier   float64       // default: 2.0
    Jitter       float64       // default: 0.2 (±20%)
    MaxRetries   int           // default: 0 (unlimited)
}
```

---

## Example CLI Tool

A complete command-line utility is included in `examples/cli`:

```bash
cd examples/cli
go run main.go -url http://localhost:8080/sse/events -event custom_event
```

---

## Running Tests

```bash
go test -v -race ./...
```

---

## License

Apache License 2.0. See [LICENSE](../../LICENSE) for details.
