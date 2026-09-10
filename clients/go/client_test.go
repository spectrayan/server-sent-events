package sseclient

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestClient_StreamEvents(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-cache")
		w.WriteHeader(http.StatusOK)

		flusher, ok := w.(http.Flusher)
		if !ok {
			t.Fatal("expected flusher")
		}

		for i := 1; i <= 3; i++ {
			fmt.Fprintf(w, "id: %d\nevent: count\ndata: {\"val\":%d}\n\n", i, i)
			flusher.Flush()
		}
	}))
	defer server.Close()

	client := New(server.URL,
		WithBufferSize(10),
		WithReconnectionConfig(ReconnectionConfig{
			InitialDelay: 50 * time.Millisecond,
			MaxDelay:     100 * time.Millisecond,
			Multiplier:   1.5,
			Jitter:       0.0,
			MaxRetries:   2,
		}),
	)

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	events, _ := client.Stream(ctx)

	var received []Event
	for e := range events {
		received = append(received, e)
		if len(received) == 3 {
			cancel() // cancel after reading 3 events
		}
	}

	if len(received) != 3 {
		t.Fatalf("expected 3 events, got %d", len(received))
	}
	if received[0].ID != "1" || received[2].ID != "3" {
		t.Errorf("unexpected event IDs: %s, %s", received[0].ID, received[2].ID)
	}
}

func TestClient_ContextCancellation(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/event-stream")
		w.WriteHeader(http.StatusOK)
		if flusher, ok := w.(http.Flusher); ok {
			fmt.Fprintf(w, ":keepalive\n\n")
			flusher.Flush()
		}
		// Hold connection open
		<-r.Context().Done()
	}))
	defer server.Close()

	client := New(server.URL)
	ctx, cancel := context.WithCancel(context.Background())

	events, _ := client.Stream(ctx)

	// Cancel almost immediately
	time.Sleep(50 * time.Millisecond)
	cancel()

	done := make(chan struct{})
	go func() {
		for range events {
		}
		close(done)
	}()

	select {
	case <-done:
		// Passed - channel was cleanly closed on cancel
	case <-time.After(1 * time.Second):
		t.Fatal("stream did not close within 1s after context cancel")
	}
}

func TestClient_AutoReconnectAndLastEventID(t *testing.T) {
	var connCount int32
	var receivedLastEventID atomic.Value

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		count := atomic.AddInt32(&connCount, 1)
		w.Header().Set("Content-Type", "text/event-stream")
		w.WriteHeader(http.StatusOK)

		flusher, _ := w.(http.Flusher)

		if count == 1 {
			// First connection: emit event id=100 and drop connection
			fmt.Fprintf(w, "id: 100\nevent: ping\ndata: first_payload\n\n")
			flusher.Flush()
			// Abruptly return to simulate network drop
			return
		}

		if count == 2 {
			// Reconnection: verify Last-Event-ID header sent by client
			lastID := r.Header.Get("Last-Event-ID")
			receivedLastEventID.Store(lastID)

			fmt.Fprintf(w, "id: 101\nevent: ping\ndata: resumed_payload\n\n")
			flusher.Flush()
			return
		}
	}))
	defer server.Close()

	client := New(server.URL,
		WithReconnectionConfig(ReconnectionConfig{
			InitialDelay: 20 * time.Millisecond,
			MaxDelay:     50 * time.Millisecond,
			Multiplier:   1.0,
			Jitter:       0.0,
		}),
	)

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	events, _ := client.Stream(ctx)

	var collected []Event
	for e := range events {
		collected = append(collected, e)
		if len(collected) == 2 {
			cancel()
		}
	}

	if len(collected) != 2 {
		t.Fatalf("expected 2 events across reconnect, got %d", len(collected))
	}
	if collected[0].ID != "100" || collected[1].ID != "101" {
		t.Errorf("unexpected IDs: %s, %s", collected[0].ID, collected[1].ID)
	}

	lastIDVal, _ := receivedLastEventID.Load().(string)
	if lastIDVal != "100" {
		t.Errorf("expected server to receive Last-Event-ID '100', got '%s'", lastIDVal)
	}
}

func TestClient_StreamEventsFiltering(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/event-stream")
		w.WriteHeader(http.StatusOK)
		flusher, _ := w.(http.Flusher)

		fmt.Fprintf(w, "event: trade\ndata: {\"symbol\":\"AAPL\"}\n\n")
		flusher.Flush()

		fmt.Fprintf(w, "event: alert\ndata: {\"level\":\"warning\"}\n\n")
		flusher.Flush()

		fmt.Fprintf(w, "event: trade\ndata: {\"symbol\":\"GOOG\"}\n\n")
		flusher.Flush()
	}))
	defer server.Close()

	client := New(server.URL)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	trades, _ := client.StreamEvents(ctx, "trade")

	var tradeEvents []Event
	for e := range trades {
		tradeEvents = append(tradeEvents, e)
		if len(tradeEvents) == 2 {
			cancel()
		}
	}

	if len(tradeEvents) != 2 {
		t.Fatalf("expected 2 trade events, got %d", len(tradeEvents))
	}
	if string(tradeEvents[0].Data) != "{\"symbol\":\"AAPL\"}" {
		t.Errorf("unexpected trade data: %s", string(tradeEvents[0].Data))
	}
}

func TestClient_HTTP204Termination(t *testing.T) {
	var requestCount int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&requestCount, 1)
		w.WriteHeader(http.StatusNoContent) // 204
	}))
	defer server.Close()

	client := New(server.URL,
		WithReconnectionConfig(ReconnectionConfig{
			InitialDelay: 20 * time.Millisecond,
			MaxDelay:     50 * time.Millisecond,
		}),
	)

	ctx, cancel := context.WithTimeout(context.Background(), 500*time.Millisecond)
	defer cancel()

	events, errs := client.Stream(ctx)

	// Consume both
	for range events {
	}
	for range errs {
	}

	// Must NOT attempt reconnect on 204
	if atomic.LoadInt32(&requestCount) != 1 {
		t.Errorf("expected exactly 1 request on 204, got %d", atomic.LoadInt32(&requestCount))
	}
}
