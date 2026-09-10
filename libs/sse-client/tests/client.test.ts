import { describe, it, expect, vi } from 'vitest';
import { createSseClient, SseClient } from '../src/sse-client';

function createMockResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  let index = 0;

  const stream = new ReadableStream<Uint8Array>({
    pull(controller) {
      if (index < chunks.length) {
        controller.enqueue(encoder.encode(chunks[index++]));
      } else {
        controller.close();
      }
    },
  });

  return new Response(stream, {
    status: 200,
    statusText: 'OK',
    headers: { 'Content-Type': 'text/event-stream' },
  });
}

describe('SseClient', () => {
  it('should stream parsed json events to subscriber', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      createMockResponse([
        'data: {"id":1,"msg":"hello"}\n\n',
        'data: {"id":2,"msg":"world"}\n\n',
      ])
    );

    const client = createSseClient({ fetch: mockFetch as any });
    const received: any[] = [];

    await new Promise<void>((resolve) => {
      const sub = client.stream('http://localhost:8080/sse', {
        reconnection: { enabled: false },
        onClose: () => resolve(),
      }, {
        next: (data) => received.push(data),
      });
    });

    expect(received).toEqual([
      { id: 1, msg: 'hello' },
      { id: 2, msg: 'world' },
    ]);
  });

  it('should filter events by event name in streamEvent', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      createMockResponse([
        'event: message\ndata: general message\n\n',
        'event: notification\ndata: alert 1\n\n',
        'event: notification\ndata: alert 2\n\n',
      ])
    );

    const client = createSseClient({ fetch: mockFetch as any });
    const notifications: string[] = [];

    await new Promise<void>((resolve) => {
      client.streamEvent('http://localhost:8080/sse', 'notification', {
        reconnection: { enabled: false },
        onClose: () => resolve(),
      }, {
        next: (data) => notifications.push(data as string),
      });
    });

    expect(notifications).toEqual(['alert 1', 'alert 2']);
  });

  it('should support async iteration with client.iterate', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      createMockResponse([
        'data: item A\n\n',
        'data: item B\n\n',
        'data: item C\n\n',
      ])
    );

    const client = createSseClient({ fetch: mockFetch as any });
    const items: string[] = [];

    for await (const item of client.iterate<string>('http://localhost:8080/sse', {
      reconnection: { enabled: false },
    })) {
      items.push(item);
    }

    expect(items).toEqual(['item A', 'item B', 'item C']);
  });

  it('should propagate Last-Event-ID header and query param on reconnect', async () => {
    let callCount = 0;
    const urlsCalled: string[] = [];
    const headersCalled: Record<string, string>[] = [];

    const mockFetch = vi.fn().mockImplementation((url, init) => {
      callCount++;
      urlsCalled.push(url);
      headersCalled.push(init?.headers || {});

      if (callCount === 1) {
        return Promise.resolve(createMockResponse(['id: evt-999\ndata: first event\n\n']));
      } else {
        return Promise.resolve(createMockResponse(['id: evt-1000\ndata: resumed event\n\n']));
      }
    });

    const client = createSseClient({ fetch: mockFetch as any });
    const events: string[] = [];

    await new Promise<void>((resolve) => {
      const sub = client.stream<string>('http://localhost:8080/sse/test', {
        reconnection: {
          enabled: true,
          maxRetries: 1,
          initialDelayMs: 10,
        },
        onClose: () => resolve(),
      }, {
        next: (data) => {
          events.push(data);
          if (events.length === 2) {
            sub.unsubscribe();
            resolve();
          }
        },
      });
    });

    expect(events).toEqual(['first event', 'resumed event']);
    expect(urlsCalled.length).toBeGreaterThanOrEqual(2);
    expect(urlsCalled[1]).toContain('lastEventId=evt-999');
    expect(headersCalled[1]['Last-Event-ID']).toBe('evt-999');
  });
});
