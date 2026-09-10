"""
Spectrayan Server-Sent Events (SSE) Client for Python.
"""

from .backoff import compute_backoff_delay
from .client import SseClient, SseConnectionError, SseError, SyncSseClient
from .models import SseEvent, SseReconnectionConfig
from .parser import W3CSseParser

__version__ = "2.0.0"

__all__ = [
    "SseClient",
    "SyncSseClient",
    "SseEvent",
    "SseReconnectionConfig",
    "SseError",
    "SseConnectionError",
    "W3CSseParser",
    "compute_backoff_delay",
]