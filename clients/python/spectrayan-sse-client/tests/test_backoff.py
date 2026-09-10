from spectrayan_sse.backoff import compute_backoff_delay
from spectrayan_sse.models import SseReconnectionConfig


def test_backoff_initial_delay():
    config = SseReconnectionConfig(initial_delay_ms=1000, multiplier=2.0, jitter=0.1, max_delay_ms=10000)
    delay = compute_backoff_delay(0, config)
    # 1000ms ± 10% = 900ms to 1100ms -> 0.9s to 1.1s
    assert 0.89 <= delay <= 1.11


def test_backoff_exponential_growth():
    config = SseReconnectionConfig(initial_delay_ms=1000, multiplier=2.0, jitter=0.0, max_delay_ms=20000)
    assert compute_backoff_delay(0, config) == 1.0
    assert compute_backoff_delay(1, config) == 2.0
    assert compute_backoff_delay(2, config) == 4.0
    assert compute_backoff_delay(3, config) == 8.0


def test_backoff_max_delay_cap():
    config = SseReconnectionConfig(initial_delay_ms=1000, multiplier=2.0, jitter=0.0, max_delay_ms=5000)
    # 2^4 * 1000 = 16000 -> capped at 5000ms = 5.0s
    assert compute_backoff_delay(4, config) == 5.0