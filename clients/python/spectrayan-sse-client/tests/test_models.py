import pytest
from pydantic import BaseModel
from spectrayan_sse.models import SseEvent, SseReconnectionConfig


class SampleModel(BaseModel):
    id: str
    count: int
    active: bool


def test_sse_event_json_data():
    event = SseEvent(data='{"key": "value", "numbers": [1, 2, 3]}')
    parsed = event.json_data()
    assert parsed == {"key": "value", "numbers": [1, 2, 3]}


def test_sse_event_parse_as_pydantic():
    event = SseEvent(data='{"id": "usr-1", "count": 10, "active": true}')
    model = event.parse_as(SampleModel)
    assert isinstance(model, SampleModel)
    assert model.id == "usr-1"
    assert model.count == 10
    assert model.active is True


def test_sse_event_parse_as_invalid():
    event = SseEvent(data="")
    with pytest.raises(ValueError, match="Cannot parse empty"):
        event.parse_as(SampleModel)


def test_reconnection_config_defaults():
    config = SseReconnectionConfig()
    assert config.enabled is True
    assert config.initial_delay_ms == 1000
    assert config.max_delay_ms == 30000
    assert config.multiplier == 1.5
    assert config.jitter == 0.2
    assert config.max_retries is None