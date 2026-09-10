package sseclient

import (
	"encoding/json"
	"time"
)

// Event represents a single Server-Sent Event conforming to the W3C EventSource specification.
type Event struct {
	// ID is the unique event identifier (W3C 'id:' field).
	ID string `json:"id,omitempty"`

	// Type is the event name (W3C 'event:' field). Defaults to "message" if omitted by server.
	Type string `json:"type,omitempty"`

	// Data is the payload bytes (concatenated W3C 'data:' lines).
	Data []byte `json:"data"`

	// Retry is the server-recommended reconnection delay (W3C 'retry:' field).
	Retry time.Duration `json:"retry,omitempty"`

	// Raw is the unparsed event block (useful for debugging and tracing).
	Raw string `json:"raw,omitempty"`
}

// JSON parses the event's Data field as JSON into the provided destination pointer.
func (e Event) JSON(v any) error {
	return json.Unmarshal(e.Data, v)
}

// Decode is an alias for JSON, unmarshaling the event's Data into v.
func (e Event) Decode(v any) error {
	return json.Unmarshal(e.Data, v)
}

// String returns the Data payload as a UTF-8 string.
func (e Event) String() string {
	return string(e.Data)
}

// Is returns true if the event Type matches the specified event name.
func (e Event) Is(eventType string) bool {
	return e.Type == eventType
}
