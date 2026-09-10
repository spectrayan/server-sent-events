package sseclient

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"sync"
	"time"
)

var (
	// ErrMaxRetriesExceeded is returned when the client exhausts all configured reconnection attempts.
	ErrMaxRetriesExceeded = errors.New("sseclient: maximum reconnection attempts exceeded")

	// ErrStreamClosedByServer is returned when the server terminates the stream with HTTP 204 No Content.
	ErrStreamClosedByServer = errors.New("sseclient: stream closed by server (HTTP 204)")
)

// Option is a functional option for configuring a Client.
type Option func(*Client)

// WithHTTPClient configures a custom *http.Client.
func WithHTTPClient(httpClient *http.Client) Option {
	return func(c *Client) {
		if httpClient != nil {
			c.httpClient = httpClient
		}
	}
}

// WithHeader sets an HTTP header sent on every connection request.
func WithHeader(key, value string) Option {
	return func(c *Client) {
		c.headers[key] = value
	}
}

// WithHeaders sets multiple HTTP headers sent on connection requests.
func WithHeaders(headers map[string]string) Option {
	return func(c *Client) {
		for k, v := range headers {
			c.headers[k] = v
		}
	}
}

// WithReconnectionConfig configures the backoff and retry settings.
func WithReconnectionConfig(config ReconnectionConfig) Option {
	return func(c *Client) {
		c.reconnectConfig = config
	}
}

// WithLastEventID sets the initial Last-Event-ID for stream resumption.
func WithLastEventID(id string) Option {
	return func(c *Client) {
		c.lastEventID = id
	}
}

// WithBufferSize sets the channel buffer capacity for events and errors. Default is 100.
func WithBufferSize(size int) Option {
	return func(c *Client) {
		if size > 0 {
			c.bufferSize = size
		}
	}
}

// Client is a resilient, channel-based Server-Sent Events client.
type Client struct {
	url             string
	httpClient      *http.Client
	headers         map[string]string
	reconnectConfig ReconnectionConfig
	lastEventID     string
	bufferSize      int
	mu              sync.RWMutex
}

// New creates a new SSE client for the specified stream endpoint.
func New(url string, opts ...Option) *Client {
	c := &Client{
		url: url,
		httpClient: &http.Client{
			Timeout: 0, // SSE streams must not have a global HTTP client timeout
		},
		headers:         make(map[string]string),
		reconnectConfig: DefaultReconnectionConfig(),
		bufferSize:      100,
	}

	for _, opt := range opts {
		opt(c)
	}

	return c
}

// LastEventID returns the most recently received event ID.
func (c *Client) LastEventID() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.lastEventID
}

// Stream opens a connection to the SSE server and streams events across channels.
// The stream continues running and automatically reconnects upon disconnection until ctx is canceled
// or the server responds with HTTP 204 No Content.
func (c *Client) Stream(ctx context.Context) (<-chan Event, <-chan error) {
	eventsChan := make(chan Event, c.bufferSize)
	errsChan := make(chan error, c.bufferSize)

	go c.run(ctx, eventsChan, errsChan)

	return eventsChan, errsChan
}

// StreamEvents returns a channel streaming only events matching the specified eventType.
func (c *Client) StreamEvents(ctx context.Context, eventType string) (<-chan Event, <-chan error) {
	filteredEvents := make(chan Event, c.bufferSize)
	errsChan := make(chan error, c.bufferSize)

	rawEvents, rawErrs := c.Stream(ctx)

	go func() {
		defer close(filteredEvents)
		defer close(errsChan)

		for {
			select {
			case <-ctx.Done():
				return

			case err, ok := <-rawErrs:
				if !ok {
					return
				}
				select {
				case errsChan <- err:
				case <-ctx.Done():
					return
				}

			case event, ok := <-rawEvents:
				if !ok {
					return
				}
				if event.Type == eventType {
					select {
					case filteredEvents <- event:
					case <-ctx.Done():
						return
					}
				}
			}
		}
	}()

	return filteredEvents, errsChan
}

func (c *Client) run(ctx context.Context, eventsChan chan<- Event, errsChan chan<- error) {
	defer close(eventsChan)
	defer close(errsChan)

	backoff := NewBackoff(c.reconnectConfig)
	attempt := 0
	parser := NewParser()

	c.mu.RLock()
	if c.lastEventID != "" {
		parser.SetLastEventID(c.lastEventID)
	}
	c.mu.RUnlock()

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}

		err := c.connectAndStream(ctx, parser, eventsChan, errsChan)
		if err == nil || errors.Is(err, context.Canceled) {
			return
		}

		if errors.Is(err, ErrStreamClosedByServer) {
			c.sendError(ctx, errsChan, err)
			return
		}

		// Notify error channel of disconnection
		c.sendError(ctx, errsChan, fmt.Errorf("sse connection drop: %w", err))

		// Check retry budget
		attempt++
		if backoff.MaxRetries() > 0 && attempt >= backoff.MaxRetries() {
			c.sendError(ctx, errsChan, ErrMaxRetriesExceeded)
			return
		}

		delay := backoff.Delay(attempt)
		select {
		case <-ctx.Done():
			return
		case <-time.After(delay):
		}
	}
}

func (c *Client) connectAndStream(
	ctx context.Context,
	parser *Parser,
	eventsChan chan<- Event,
	errsChan chan<- error,
) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.url, nil)
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	// Standard SSE request headers
	req.Header.Set("Accept", "text/event-stream")
	req.Header.Set("Cache-Control", "no-cache")
	req.Header.Set("Connection", "keep-alive")

	// Custom configured headers
	for k, v := range c.headers {
		req.Header.Set(k, v)
	}

	// Resume via Last-Event-ID if available
	lastID := parser.LastEventID()
	if lastID != "" {
		req.Header.Set("Last-Event-ID", lastID)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	// W3C: HTTP 204 resets the connection and terminates the stream
	if resp.StatusCode == http.StatusNoContent {
		return ErrStreamClosedByServer
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("unexpected HTTP status %d: %s", resp.StatusCode, resp.Status)
	}

	// Reset parser state for new connection (preserving Last-Event-ID)
	parser.Reset()

	return parser.ParseReader(resp.Body, func(e Event) error {
		// Update tracked Last-Event-ID
		if e.ID != "" {
			c.mu.Lock()
			c.lastEventID = e.ID
			c.mu.Unlock()
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		case eventsChan <- e:
			return nil
		}
	})
}

func (c *Client) sendError(ctx context.Context, errsChan chan<- error, err error) {
	select {
	case <-ctx.Done():
	case errsChan <- err:
	default:
		// Drop error if buffer full to avoid deadlock
	}
}
