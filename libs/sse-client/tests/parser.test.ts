import { describe, it, expect } from 'vitest';
import { SseChunkParser } from '../src/parser';
import { SseEvent } from '../src/types';

describe('SseChunkParser', () => {
  it('should parse standard single-line data events', () => {
    const parser = new SseChunkParser();
    const events: SseEvent<string>[] = [];

    parser.feed('data: hello world\n\n', (e) => events.push(e));

    expect(events.length).toBe(1);
    expect(events[0].data).toBe('hello world');
    expect(events[0].event).toBeUndefined();
  });

  it('should parse multiline data events with newline joins', () => {
    const parser = new SseChunkParser();
    const events: SseEvent<string>[] = [];

    parser.feed('data: line one\ndata: line two\n\n', (e) => events.push(e));

    expect(events.length).toBe(1);
    expect(events[0].data).toBe('line one\nline two');
  });

  it('should parse named events, id, and retry', () => {
    const parser = new SseChunkParser();
    const events: SseEvent<string>[] = [];

    parser.feed('event: alert\nid: 42\nretry: 5000\ndata: system warning\n\n', (e) =>
      events.push(e)
    );

    expect(events.length).toBe(1);
    expect(events[0].event).toBe('alert');
    expect(events[0].id).toBe('42');
    expect(events[0].retry).toBe(5000);
    expect(events[0].data).toBe('system warning');
  });

  it('should ignore comment lines such as :keepalive heartbeats', () => {
    const parser = new SseChunkParser();
    const events: SseEvent<string>[] = [];

    parser.feed(': keepalive\n: this is a comment\ndata: actual payload\n\n', (e) =>
      events.push(e)
    );

    expect(events.length).toBe(1);
    expect(events[0].data).toBe('actual payload');
  });

  it('should buffer and assemble chunk fragments across packet boundaries', () => {
    const parser = new SseChunkParser();
    const events: SseEvent<string>[] = [];

    parser.feed('data: hel', (e) => events.push(e));
    expect(events.length).toBe(0);

    parser.feed('lo from ch', (e) => events.push(e));
    expect(events.length).toBe(0);

    parser.feed('unk!\n\n', (e) => events.push(e));
    expect(events.length).toBe(1);
    expect(events[0].data).toBe('hello from chunk!');
  });
});
