package sseclient

import (
	"bufio"
	"bytes"
	"errors"
	"io"
	"strconv"
	"strings"
	"time"
)

// Parser parses Server-Sent Events according to the W3C EventSource specification.
type Parser struct {
	lastEventID string
	currID      string
	currType    string
	dataBuf     bytes.Buffer
	currRetry   time.Duration
	rawBuf      bytes.Buffer
}

// NewParser creates a new, stateful SSE parser.
func NewParser() *Parser {
	return &Parser{}
}

// LastEventID returns the most recently received event ID.
func (p *Parser) LastEventID() string {
	return p.lastEventID
}

// SetLastEventID manually overrides or pre-seeds the Last-Event-ID.
func (p *Parser) SetLastEventID(id string) {
	p.lastEventID = id
}

// Reset clears the internal buffer and in-progress event fields while preserving LastEventID.
func (p *Parser) Reset() {
	p.currID = ""
	p.currType = ""
	p.dataBuf.Reset()
	p.currRetry = 0
	p.rawBuf.Reset()
}

// ParseLine processes a single SSE protocol line.
// When an event frame finishes (blank line with accumulated data), returns (*Event, true, nil).
func (p *Parser) ParseLine(line string) (*Event, bool, error) {
	// Track raw line
	if p.rawBuf.Len() > 0 {
		p.rawBuf.WriteString("\n")
	}
	p.rawBuf.WriteString(line)

	// 1. Empty line -> Dispatch event
	if len(line) == 0 {
		if p.dataBuf.Len() == 0 {
			// Blank line with no data: reset transient fields
			p.currID = ""
			p.currType = ""
			p.rawBuf.Reset()
			return nil, false, nil
		}

		// Remove trailing newline from dataBuf
		dataBytes := p.dataBuf.Bytes()
		if len(dataBytes) > 0 && dataBytes[len(dataBytes)-1] == '\n' {
			dataBytes = dataBytes[:len(dataBytes)-1]
		}

		eventType := p.currType
		if eventType == "" {
			eventType = "message"
		}

		eventID := p.currID
		if eventID == "" {
			eventID = p.lastEventID
		}

		event := &Event{
			ID:    eventID,
			Type:  eventType,
			Data:  append([]byte(nil), dataBytes...),
			Retry: p.currRetry,
			Raw:   p.rawBuf.String(),
		}

		// Reset current event buffer
		p.currID = ""
		p.currType = ""
		p.dataBuf.Reset()
		p.currRetry = 0
		p.rawBuf.Reset()

		return event, true, nil
	}

	// 2. Comment line (starts with ':')
	if strings.HasPrefix(line, ":") {
		return nil, false, nil
	}

	// 3. Field parsing
	var field, value string
	colonIdx := strings.IndexByte(line, ':')
	if colonIdx >= 0 {
		field = line[:colonIdx]
		value = line[colonIdx+1:]
		// Strip single leading space if present
		if strings.HasPrefix(value, " ") {
			value = value[1:]
		}
	} else {
		field = line
		value = ""
	}

	switch field {
	case "data":
		p.dataBuf.WriteString(value)
		p.dataBuf.WriteString("\n")

	case "event":
		p.currType = value

	case "id":
		// Per W3C spec: if value contains null character, ignore. Otherwise update.
		if !strings.ContainsRune(value, '\x00') {
			p.currID = value
			p.lastEventID = value
		}

	case "retry":
		if millis, err := strconv.ParseInt(strings.TrimSpace(value), 10, 64); err == nil && millis >= 0 {
			p.currRetry = time.Duration(millis) * time.Millisecond
		}
	}

	return nil, false, nil
}

// ParseReader consumes lines from an io.Reader and dispatches completed events to onEvent.
func (p *Parser) ParseReader(r io.Reader, onEvent func(Event) error) error {
	br := bufio.NewReader(r)

	for {
		line, err := readLine(br)
		if err != nil {
			if errors.Is(err, io.EOF) {
				// Flush any pending event on clean EOF if dataBuf has content
				if event, ok, _ := p.ParseLine(""); ok && event != nil {
					_ = onEvent(*event)
				}
				return io.EOF
			}
			return err
		}

		event, ok, parseErr := p.ParseLine(line)
		if parseErr != nil {
			return parseErr
		}
		if ok && event != nil {
			if err := onEvent(*event); err != nil {
				return err
			}
		}
	}
}

// readLine reads until LF, CRLF, or CR delimiter from a bufio.Reader.
func readLine(r *bufio.Reader) (string, error) {
	var buf bytes.Buffer
	for {
		b, err := r.ReadByte()
		if err != nil {
			if buf.Len() > 0 {
				return buf.String(), nil
			}
			return "", err
		}
		if b == '\n' {
			return buf.String(), nil
		}
		if b == '\r' {
			// Check for subsequent \n (CRLF)
			next, err := r.Peek(1)
			if err == nil && len(next) > 0 && next[0] == '\n' {
				_, _ = r.ReadByte()
			}
			return buf.String(), nil
		}
		buf.WriteByte(b)
	}
}
