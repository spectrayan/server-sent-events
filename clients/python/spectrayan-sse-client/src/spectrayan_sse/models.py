"""
Data models and configuration for spectrayan-sse-client.
"""

from __future__ import annotations

import json
from typing import Any, Optional, Type, TypeVar
from pydantic import BaseModel, ConfigDict, Field

T = TypeVar("T", bound=BaseModel)


class SseEvent(BaseModel):
    """
    Representation of a Server-Sent Event conforming to the W3C EventSource standard.
    """
    model_config = ConfigDict(frozen=True)

    id: Optional[str] = Field(default=None, description="Event ID for Last-Event-ID resumption")
    event: str = Field(default="message", description="Event name / type")
    data: str = Field(default="", description="Decoded event data payload")
    retry: Optional[int] = Field(default=None, description="Reconnection retry interval suggested by server (ms)")
    raw_data: str = Field(default="", description="Raw unparsed data payload")

    def json_data(self) -> Any:
        """Parse `data` as generic JSON dictionary or list."""
        if not self.data:
            return None
        return json.loads(self.data)

    def parse_as(self, model_cls: Type[T]) -> T:
        """
        Parse `data` into a strongly-typed Pydantic V2 model.
        """
        if not self.data:
            raise ValueError("Cannot parse empty SSE event data into Pydantic model")
        return model_cls.model_validate_json(self.data)


class SseReconnectionConfig(BaseModel):
    """
    Configuration options for exponential backoff and jittered reconnect behavior.
    """
    model_config = ConfigDict(frozen=True)

    enabled: bool = Field(default=True, description="Whether auto-reconnection is enabled")
    initial_delay_ms: int = Field(default=1000, description="Initial retry delay in milliseconds")
    max_delay_ms: int = Field(default=30000, description="Maximum retry delay ceiling in milliseconds")
    multiplier: float = Field(default=1.5, description="Backoff multiplier per retry attempt")
    jitter: float = Field(default=0.2, description="Random jitter percentage (e.g. 0.2 = ±20%)")
    max_retries: Optional[int] = Field(default=None, description="Max reconnection attempts (None = infinite)")