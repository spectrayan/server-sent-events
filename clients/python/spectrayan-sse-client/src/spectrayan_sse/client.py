"""
Modern, resilient Server-Sent Events (SSE) clients for Python using httpx and Pydantic V2.
"""

from __future__ import annotations

import asyncio
import logging
import time
from typing import (
    AsyncGenerator,
    Dict,
    Generator,
    Optional,
    Type,
    TypeVar,
)

import httpx
from pydantic import BaseModel

from .backoff import compute_backoff_delay
from .models import SseEvent, SseReconnectionConfig
from .parser import W3CSseParser

logger = logging.getLogger("spectrayan_sse")

T = TypeVar("T", bound=BaseModel)


class SseError(Exception):
    """Base exception for all SSE client errors."""
    pass


class SseConnectionError(SseError):
    """Raised when connection fails and reconnection attempts are exhausted or disabled."""
    pass


class SseClient:
    """
    Asynchronous Server-Sent Events client using httpx.
    Supports auto-reconnect with jittered exponential backoff, Last-Event-ID persistence,
    and Pydantic V2 typed deserialization.
    """

    def __init__(
        self,
        url: str,
        *,
        headers: Optional[Dict[str, str]] = None,
        reconnection: Optional[SseReconnectionConfig] = None,
        client: Optional[httpx.AsyncClient] = None,
        timeout: Optional[float] = 60.0,
    ) -> None:
        self.url = url
        self.custom_headers = headers or {}
        self.reconnection = reconnection or SseReconnectionConfig()
        self._external_client = client is not None
        self._client = client or httpx.AsyncClient(timeout=timeout)
        self._closed = False
        self._active_response: Optional[httpx.Response] = None

    async def __aenter__(self) -> "SseClient":
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        await self.aclose()

    async def aclose(self) -> None:
        """Closes any active streams and underlying HTTP client."""
        self._closed = True
        if self._active_response:
            await self._active_response.aclose()
            self._active_response = None
        if not self._external_client:
            await self._client.aclose()

    async def stream(self) -> AsyncGenerator[SseEvent, None]:
        """
        Asynchronously streams SSE events with automated reconnection and Last-Event-ID resumption.
        """
        parser = W3CSseParser()
        attempt = 0

        while not self._closed:
            request_headers = {
                "Accept": "text/event-stream",
                "Cache-Control": "no-cache",
                **self.custom_headers,
            }

            if parser.last_event_id:
                request_headers["Last-Event-ID"] = parser.last_event_id

            try:
                async with self._client.stream("GET", self.url, headers=request_headers) as response:
                    self._active_response = response

                    if response.status_code != 200:
                        raise SseConnectionError(
                            f"SSE connection to {self.url} failed with HTTP status {response.status_code}: {response.reason_phrase}"
                        )

                    content_type = response.headers.get("content-type", "")
                    if "text/event-stream" not in content_type:
                        logger.warning(
                            "Content-Type '%s' does not contain 'text/event-stream'", content_type
                        )

                    # Reset reconnect attempts on successful stream open
                    attempt = 0

                    async for chunk in response.aiter_text():
                        if self._closed:
                            break
                        for event in parser.feed(chunk):
                            yield event

                    # Stream completed gracefully from server EOF
                    for event in parser.flush():
                        yield event

                    if not self.reconnection.enabled:
                        break

            except (httpx.HTTPError, SseConnectionError) as exc:
                if self._closed:
                    break

                if not self.reconnection.enabled:
                    raise SseConnectionError(f"SSE connection failed: {exc}") from exc

                if (
                    self.reconnection.max_retries is not None
                    and attempt >= self.reconnection.max_retries
                ):
                    raise SseConnectionError(
                        f"SSE reconnection failed after {attempt} attempts: {exc}"
                    ) from exc

                delay = compute_backoff_delay(attempt, self.reconnection)
                logger.info(
                    "SSE stream disconnected (%s). Reconnecting in %.2fs (attempt %d)...",
                    exc,
                    delay,
                    attempt + 1,
                )
                attempt += 1
                await asyncio.sleep(delay)

            except asyncio.CancelledError:
                self._closed = True
                raise

            finally:
                self._active_response = None

    async def stream_events(
        self,
        model_cls: Type[T],
        *,
        event_type: Optional[str] = None,
    ) -> AsyncGenerator[T, None]:
        """
        Streams events filtered by event name (optional) and parsed into a Pydantic V2 model.
        """
        async for event in self.stream():
            if event_type is not None and event.event != event_type:
                continue
            if not event.data:
                continue
            yield event.parse_as(model_cls)


class SyncSseClient:
    """
    Synchronous Server-Sent Events client using httpx.Client.
    Provides standard generator streaming for non-async Python applications.
    """

    def __init__(
        self,
        url: str,
        *,
        headers: Optional[Dict[str, str]] = None,
        reconnection: Optional[SseReconnectionConfig] = None,
        client: Optional[httpx.Client] = None,
        timeout: Optional[float] = 60.0,
    ) -> None:
        self.url = url
        self.custom_headers = headers or {}
        self.reconnection = reconnection or SseReconnectionConfig()
        self._external_client = client is not None
        self._client = client or httpx.Client(timeout=timeout)
        self._closed = False
        self._active_response: Optional[httpx.Response] = None

    def __enter__(self) -> "SyncSseClient":
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.close()

    def close(self) -> None:
        """Closes active streams and underlying HTTP client."""
        self._closed = True
        if self._active_response:
            self._active_response.close()
            self._active_response = None
        if not self._external_client:
            self._client.close()

    def stream(self) -> Generator[SseEvent, None, None]:
        """
        Synchronously streams SSE events with automated reconnection and Last-Event-ID resumption.
        """
        parser = W3CSseParser()
        attempt = 0

        while not self._closed:
            request_headers = {
                "Accept": "text/event-stream",
                "Cache-Control": "no-cache",
                **self.custom_headers,
            }

            if parser.last_event_id:
                request_headers["Last-Event-ID"] = parser.last_event_id

            try:
                with self._client.stream("GET", self.url, headers=request_headers) as response:
                    self._active_response = response

                    if response.status_code != 200:
                        raise SseConnectionError(
                            f"SSE connection to {self.url} failed with HTTP status {response.status_code}"
                        )

                    attempt = 0

                    for chunk in response.iter_text():
                        if self._closed:
                            break
                        for event in parser.feed(chunk):
                            yield event

                    for event in parser.flush():
                        yield event

                    if not self.reconnection.enabled:
                        break

            except (httpx.HTTPError, SseConnectionError) as exc:
                if self._closed:
                    break

                if not self.reconnection.enabled:
                    raise SseConnectionError(f"SSE connection failed: {exc}") from exc

                if (
                    self.reconnection.max_retries is not None
                    and attempt >= self.reconnection.max_retries
                ):
                    raise SseConnectionError(
                        f"SSE reconnection failed after {attempt} attempts: {exc}"
                    ) from exc

                delay = compute_backoff_delay(attempt, self.reconnection)
                attempt += 1
                time.sleep(delay)

            finally:
                self._active_response = None

    def stream_events(
        self,
        model_cls: Type[T],
        *,
        event_type: Optional[str] = None,
    ) -> Generator[T, None, None]:
        """
        Synchronously streams events filtered by event name and parsed into a Pydantic V2 model.
        """
        for event in self.stream():
            if event_type is not None and event.event != event_type:
                continue
            if not event.data:
                continue
            yield event.parse_as(model_cls)