from spectrayan_sse.parser import W3CSseParser


def test_parse_single_event():
    parser = W3CSseParser()
    events = list(parser.feed("data: hello world\n\n"))
    assert len(events) == 1
    assert events[0].event == "message"
    assert events[0].data == "hello world"
    assert events[0].id is None


def test_parse_multiline_data():
    parser = W3CSseParser()
    payload = "data: first line\ndata: second line\ndata: third line\n\n"
    events = list(parser.feed(payload))
    assert len(events) == 1
    assert events[0].data == "first line\nsecond line\nthird line"


def test_parse_named_event_and_id():
    parser = W3CSseParser()
    payload = "event: custom-event\nid: evt-42\nretry: 3000\ndata: test\n\n"
    events = list(parser.feed(payload))
    assert len(events) == 1
    assert events[0].event == "custom-event"
    assert events[0].id == "evt-42"
    assert events[0].retry == 3000
    assert events[0].data == "test"
    assert parser.last_event_id == "evt-42"


def test_ignore_keepalive_comments():
    parser = W3CSseParser()
    payload = ":keepalive ping\n:another comment\ndata: payload\n\n"
    events = list(parser.feed(payload))
    assert len(events) == 1
    assert events[0].data == "payload"


def test_chunked_streaming_across_boundaries():
    parser = W3CSseParser()
    # Feed arbitrary splits
    e1 = list(parser.feed("da"))
    assert len(e1) == 0
    e2 = list(parser.feed("ta: split"))
    assert len(e2) == 0
    e3 = list(parser.feed(" chunk\n\n"))
    assert len(e3) == 1
    assert e3[0].data == "split chunk"


def test_parser_flush_at_eof():
    parser = W3CSseParser()
    events = list(parser.feed("data: trailing message"))
    assert len(events) == 0
    flushed = list(parser.flush())
    assert len(flushed) == 1
    assert flushed[0].data == "trailing message"