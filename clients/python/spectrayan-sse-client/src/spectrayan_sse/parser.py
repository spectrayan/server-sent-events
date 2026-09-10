"""
W3C compliant Server-Sent Events (SSE) streaming line and chunk parser.
"""

from __future__ import annotations

from typing import Generator, List, Optional
from .models import SseEvent


class W3CSseParser:
    """
    Stateful parser for streaming Server-Sent Events chunks according to W3C specification.
    Supports multiline data:, named events, id tracking, retry intervals, and comment filtering.
    """

    def __init__(self) -> None:
        self._buffer: str = ""
        self._last_event_id: Optional[str] = None
        self._current_event_type: str = "message"
        self._current_data_lines: List[str] = []
        self._current_retry: Optional[int] = None
        self._has_event_data: bool = False

    @property
    def last_event_id(self) -> Optional[str]:
        return self._last_event_id

    def feed(self, chunk: str) -> Generator[SseEvent, None, None]:
        """
        Feeds an incoming raw string chunk into the buffer and yields any fully completed SseEvents.
        """
        self._buffer += chunk
        
        # Normalize line endings to \n
        # Replace \r\n with \n, then any remaining standalone \r with \n
        # But beware that \r might be the last char in buffer (part of incoming \r\n across chunks)
        if self._buffer.endswith("\r"):
            to_process = self._buffer[:-1]
            self._buffer = "\r"
        else:
            to_process = self._buffer
            self._buffer = ""

        to_process = to_process.replace("\r\n", "\n").replace("\r", "\n")
        lines = to_process.split("\n")
        
        # The last element might be incomplete unless chunk ended with newline
        self._buffer = lines.pop() + self._buffer

        for line in lines:
            event = self._process_line(line)
            if event is not None:
                yield event

    def flush(self) -> Generator[SseEvent, None, None]:
        """
        Flushes remaining buffer and yields any final un-dispatched event at stream EOF.
        """
        if self._buffer:
            line = self._buffer.replace("\r\n", "").replace("\r", "").replace("\n", "")
            self._buffer = ""
            if line:
                event = self._process_line(line)
                if event is not None:
                    yield event

        if self._has_event_data or self._current_data_lines:
            event = self._dispatch_event()
            if event is not None:
                yield event

    def _process_line(self, line: str) -> Optional[SseEvent]:
        # Empty line triggers event dispatch
        if not line:
            return self._dispatch_event()

        # Comment line (keepalive / heartbeat)
        if line.startswith(":"):
            return None

        if ":" in line:
            field, value = line.split(":", 1)
            if value.startswith(" "):
                value = value[1:]
        else:
            field = line
            value = ""

        if field == "data":
            self._current_data_lines.append(value)
            self._has_event_data = True
        elif field == "event":
            self._current_event_type = value
            self._has_event_data = True
        elif field == "id":
            self._last_event_id = value
            self._has_event_data = True
        elif field == "retry":
            try:
                self._current_retry = int(value.strip())
            except ValueError:
                pass

        return None

    def _dispatch_event(self) -> Optional[SseEvent]:
        if not self._has_event_data and not self._current_data_lines:
            return None

        data_str = "\n".join(self._current_data_lines)
        event = SseEvent(
            id=self._last_event_id,
            event=self._current_event_type or "message",
            data=data_str,
            retry=self._current_retry,
            raw_data=data_str,
        )

        # Reset per-event fields
        self._current_event_type = "message"
        self._current_data_lines = []
        self._current_retry = None
        self._has_event_data = False

        return event