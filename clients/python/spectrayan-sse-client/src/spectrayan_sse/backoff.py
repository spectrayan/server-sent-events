"""
Jittered exponential backoff implementation for SSE reconnection.
"""

from __future__ import annotations

import random
from .models import SseReconnectionConfig


def compute_backoff_delay(attempt: int, config: SseReconnectionConfig) -> float:
    """
    Computes jittered exponential backoff delay in seconds:
    delay = min(initial * multiplier^attempt, max) * (1 ± jitter)
    """
    raw_delay_ms = min(
        float(config.initial_delay_ms) * (config.multiplier ** attempt),
        float(config.max_delay_ms)
    )
    jitter_range = raw_delay_ms * config.jitter
    min_delay = max(0.0, raw_delay_ms - jitter_range)
    max_delay = raw_delay_ms + jitter_range
    delay_ms = random.uniform(min_delay, max_delay)
    return delay_ms / 1000.0