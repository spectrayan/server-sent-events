import httpx
import pytest
from pydantic import BaseModel
from spectrayan_sse.client import SseClient, SseConnectionError, SyncSseClient
from spectrayan_sse.models import SseReconnectionConfig


class Metric(BaseModel):
    name: str
    value: float


@pytest.mark.asyncio
async def test_client_stream_success():
    def mock_handler(request: httpx.Request) -> httpx.Response:
        assert request.headers["Accept"] == "text/event-stream"
        assert request.headers["X-Custom"] == "foo"
        body = b"id: 1\nevent: update\ndata: hello\n\n"
        return httpx.Response(200, headers={"Content-Type": "text/event-stream"}, content=body)

    transport = httpx.MockTransport(mock_handler)
    async with httpx.AsyncClient(transport=transport) as http_client:
        client = SseClient(
            "http://test/sse",
            headers={"X-Custom": "foo"},
            reconnection=SseReconnectionConfig(enabled=False),
            client=http_client,
        )

        events = []
        async for event in client.stream():
            events.append(event)

        assert len(events) == 1
        assert events[0].id == "1"
        assert events[0].event == "update"
        assert events[0].data == "hello"


@pytest.mark.asyncio
async def test_client_stream_events_pydantic():
    def mock_handler(request: httpx.Request) -> httpx.Response:
        body = b'event: metric\ndata: {"name": "cpu", "value": 85.5}\n\n'
        return httpx.Response(200, headers={"Content-Type": "text/event-stream"}, content=body)

    transport = httpx.MockTransport(mock_handler)
    async with httpx.AsyncClient(transport=transport) as http_client:
        client = SseClient(
            "http://test/sse",
            reconnection=SseReconnectionConfig(enabled=False),
            client=http_client,
        )

        metrics = []
        async for metric in client.stream_events(Metric, event_type="metric"):
            metrics.append(metric)

        assert len(metrics) == 1
        assert metrics[0].name == "cpu"
        assert metrics[0].value == 85.5


@pytest.mark.asyncio
async def test_client_reconnection_last_event_id():
    calls = 0
    received_headers = []

    def mock_handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        received_headers.append(dict(request.headers))

        if calls == 1:
            # First call sends an event with id: evt-1, then terminates
            body = b"id: evt-1\ndata: first\n\n"
            return httpx.Response(200, headers={"Content-Type": "text/event-stream"}, content=body)
        else:
            # Second call sends second event, then we close
            body = b"id: evt-2\ndata: second\n\n"
            return httpx.Response(200, headers={"Content-Type": "text/event-stream"}, content=body)

    transport = httpx.MockTransport(mock_handler)
    async with httpx.AsyncClient(transport=transport) as http_client:
        client = SseClient(
            "http://test/sse",
            reconnection=SseReconnectionConfig(enabled=True, initial_delay_ms=10, max_retries=2, jitter=0.0),
            client=http_client,
        )

        events = []
        async for event in client.stream():
            events.append(event)
            if len(events) == 2:
                await client.aclose()
                break

        assert len(events) == 2
        assert events[0].id == "evt-1"
        assert events[1].id == "evt-2"
        # Check that second request included Last-Event-ID
        assert "Last-Event-ID" not in received_headers[0] or received_headers[0]["Last-Event-ID"] == ""
        assert received_headers[1].get("last-event-id") == "evt-1"


@pytest.mark.asyncio
async def test_client_http_error_raises_when_reconnect_disabled():
    def mock_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, content=b"Server Error")

    transport = httpx.MockTransport(mock_handler)
    async with httpx.AsyncClient(transport=transport) as http_client:
        client = SseClient(
            "http://test/sse",
            reconnection=SseReconnectionConfig(enabled=False),
            client=http_client,
        )

        with pytest.raises(SseConnectionError, match="status 500"):
            async for _ in client.stream():
                pass


def test_sync_client_streaming():
    def mock_handler(request: httpx.Request) -> httpx.Response:
        body = b'data: {"name": "mem", "value": 42.0}\n\n'
        return httpx.Response(200, headers={"Content-Type": "text/event-stream"}, content=body)

    transport = httpx.MockTransport(mock_handler)
    with httpx.Client(transport=transport) as http_client:
        client = SyncSseClient(
            "http://test/sse",
            reconnection=SseReconnectionConfig(enabled=False),
            client=http_client,
        )

        events = list(client.stream())
        assert len(events) == 1
        assert events[0].data == '{"name": "mem", "value": 42.0}'

        items = list(client.stream_events(Metric))
        assert len(items) == 1
        assert items[0].name == "mem"
        assert items[0].value == 42.0