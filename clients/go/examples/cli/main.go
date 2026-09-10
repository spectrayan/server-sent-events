package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	sseclient "github.com/spectrayan/server-sent-events/clients/go"
)

func main() {
	url := flag.String("url", "http://localhost:8080/sse/events", "SSE stream endpoint URL")
	eventType := flag.String("event", "", "Optional event type filter")
	flag.Parse()

	log.Printf("Connecting to SSE stream at %s ...", *url)

	client := sseclient.New(*url,
		sseclient.WithReconnectionConfig(sseclient.ReconnectionConfig{
			InitialDelay: 1 * time.Second,
			MaxDelay:     15 * time.Second,
			Multiplier:   2.0,
			Jitter:       0.2,
		}),
	)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	var events <-chan sseclient.Event
	var errs <-chan error

	if *eventType != "" {
		events, errs = client.StreamEvents(ctx, *eventType)
	} else {
		events, errs = client.Stream(ctx)
	}

	for {
		select {
		case <-ctx.Done():
			log.Println("Received termination signal, shutting down SSE stream...")
			return

		case err, ok := <-errs:
			if !ok {
				return
			}
			log.Printf("[STREAM NOTICE] %v", err)

		case event, ok := <-events:
			if !ok {
				log.Println("Stream channel closed")
				return
			}
			fmt.Printf("id: %-10s | type: %-15s | data: %s\n", event.ID, event.Type, event.String())
		}
	}
}
