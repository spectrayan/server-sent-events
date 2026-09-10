package sseclient

import (
	"strings"
	"testing"
	"time"
)

func TestParser_SingleLineEvent(t *testing.T) {
	input := "id: 101\nevent: user_joined\ndata: {\"user\":\"alice\"}\n\n"
	parser := NewParser()

	var events []Event
	err := parser.ParseReader(strings.NewReader(input), func(e Event) error {
		events = append(events, e)
		return nil
	})
	if err != nil && err.Error() != "EOF" {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(events) != 1 {
		t.Fatalf("expected 1 event, got %d", len(events))
	}

	e := events[0]
	if e.ID != "101" {
		t.Errorf("expected ID '101', got '%s'", e.ID)
	}
	if e.Type != "user_joined" {
		t.Errorf("expected Type 'user_joined', got '%s'", e.Type)
	}
	if string(e.Data) != "{\"user\":\"alice\"}" {
		t.Errorf("expected Data '{\"user\":\"alice\"}', got '%s'", string(e.Data))
	}
}

func TestParser_MultiLineDataAndComments(t *testing.T) {
	input := ":keepalive ping\n" +
		"data: line one\n" +
		"data: line two\n" +
		"data: line three\n" +
		"\n"

	parser := NewParser()
	var events []Event
	err := parser.ParseReader(strings.NewReader(input), func(e Event) error {
		events = append(events, e)
		return nil
	})
	if err != nil && err.Error() != "EOF" {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(events) != 1 {
		t.Fatalf("expected 1 event, got %d", len(events))
	}

	e := events[0]
	expectedData := "line one\nline two\nline three"
	if string(e.Data) != expectedData {
		t.Errorf("expected multi-line data %q, got %q", expectedData, string(e.Data))
	}
	if e.Type != "message" {
		t.Errorf("expected default Type 'message', got '%s'", e.Type)
	}
}

func TestParser_RetryAndLastEventID(t *testing.T) {
	input := "id: evt-42\nretry: 5000\ndata: first\n\nid: evt-43\ndata: second\n\n"
	parser := NewParser()

	var events []Event
	_ = parser.ParseReader(strings.NewReader(input), func(e Event) error {
		events = append(events, e)
		return nil
	})

	if len(events) != 2 {
		t.Fatalf("expected 2 events, got %d", len(events))
	}

	if events[0].Retry != 5000*time.Millisecond {
		t.Errorf("expected retry 5s, got %v", events[0].Retry)
	}
	if parser.LastEventID() != "evt-43" {
		t.Errorf("expected LastEventID 'evt-43', got '%s'", parser.LastEventID())
	}
}

func TestParser_CRLFLineEndings(t *testing.T) {
	input := "id: win-1\r\nevent: windows\r\ndata: crlf payload\r\n\r\n"
	parser := NewParser()

	var events []Event
	_ = parser.ParseReader(strings.NewReader(input), func(e Event) error {
		events = append(events, e)
		return nil
	})

	if len(events) != 1 {
		t.Fatalf("expected 1 event, got %d", len(events))
	}
	if events[0].ID != "win-1" || events[0].Type != "windows" || string(events[0].Data) != "crlf payload" {
		t.Errorf("unexpected parsed event: %+v", events[0])
	}
}
