import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SseClient } from './sse-client';
import { SSE_CLIENT_CONFIG, SseClientConfig } from './config';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';

/**
 * Tests for the fetch-based SSE transport.
 *
 * Mocks the global `fetch()` to simulate SSE text/event-stream responses,
 * verifying that the fetch transport correctly:
 *  - Sends custom headers (Authorization)
 *  - Parses SSE text protocol (data, event, id fields)
 *  - Emits parsed events to the Observable
 *  - Cleans up via AbortController on unsubscribe
 */

// Helper: create a mock ReadableStream from SSE text chunks
function createMockSseStream(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  let index = 0;
  return new ReadableStream<Uint8Array>({
    pull(controller) {
      if (index < chunks.length) {
        controller.enqueue(encoder.encode(chunks[index]));
        index++;
      } else {
        controller.close();
      }
    },
  });
}

// Helper: create a mock fetch Response with SSE content-type
function createMockResponse(chunks: string[]): Response {
  return {
    ok: true,
    status: 200,
    statusText: 'OK',
    headers: new Headers({ 'Content-Type': 'text/event-stream' }),
    body: createMockSseStream(chunks),
  } as unknown as Response;
}

describe('SseClient (fetch transport)', () => {
  let service: SseClient;
  let originalFetch: typeof globalThis.fetch;
  let capturedFetchArgs: { url: string; init: RequestInit } | null = null;

  beforeEach(() => {
    vi.useFakeTimers();
    capturedFetchArgs = null;

    // Save original fetch
    originalFetch = globalThis.fetch;

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        SseClient,
        {
          provide: SSE_CLIENT_CONFIG,
          useValue: {
            transport: 'fetch',
            reconnection: {
              enabled: false,
              initialDelayMs: 10,
              maxDelayMs: 20,
              backoffMultiplier: 1,
              jitterRatio: 0,
              maxRetries: 0,
            },
          } satisfies Partial<SseClientConfig>,
        },
      ],
    });

    service = TestBed.inject(SseClient);
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.useRealTimers();
  });

  it('should send custom Authorization header with fetch transport', (done: any) => {
    const sseChunks = [
      'data: {"msg":"hello"}\n\n',
    ];

    globalThis.fetch = vi.fn((url: string | URL | Request, init?: RequestInit) => {
      capturedFetchArgs = { url: url as string, init: init! };
      return Promise.resolve(createMockResponse(sseChunks));
    }) as any;

    const sub = service.stream<{ msg: string }>('http://test/sse/topic', {
      transport: 'fetch',
      headers: { 'Authorization': 'Bearer test-jwt-token' },
    }).subscribe({
      next: (v) => {
        expect(v.msg).toBe('hello');

        // Verify the Authorization header was sent
        expect(capturedFetchArgs).toBeTruthy();
        const headers = capturedFetchArgs!.init.headers as Record<string, string>;
        expect(headers['Authorization']).toBe('Bearer test-jwt-token');
        expect(headers['Accept']).toBe('text/event-stream');

        sub.unsubscribe();
        done();
      },
      error: done,
    });
  });

  it('should parse multi-line SSE data fields', (done: any) => {
    const sseChunks = [
      'data: line1\ndata: line2\n\n',
    ];

    globalThis.fetch = vi.fn(() =>
      Promise.resolve(createMockResponse(sseChunks))
    ) as any;

    const sub = service.stream<string>('http://test/sse', {
      transport: 'fetch',
      parse: (raw: string) => raw, // raw string, don't JSON.parse
    }).subscribe({
      next: (v) => {
        expect(v).toBe('line1\nline2');
        sub.unsubscribe();
        done();
      },
      error: done,
    });
  });

  it('should parse named SSE events', (done: any) => {
    const sseChunks = [
      'event: notification\ndata: {"type":"alert"}\n\n',
    ];

    globalThis.fetch = vi.fn(() =>
      Promise.resolve(createMockResponse(sseChunks))
    ) as any;

    const sub = service.stream<{ type: string }>('http://test/sse', {
      transport: 'fetch',
      events: ['notification'],
    }).subscribe({
      next: (v) => {
        expect(v.type).toBe('alert');
        sub.unsubscribe();
        done();
      },
      error: done,
    });
  });

  it('should abort fetch on unsubscribe', async () => {
    let abortSignal: AbortSignal | undefined;

    // A stream that never closes (simulates long-lived SSE)
    const neverEndingStream = new ReadableStream<Uint8Array>({
      start() {
        // Never enqueue, never close — simulates waiting for events
      },
    });

    globalThis.fetch = vi.fn((_url: string | URL | Request, init?: RequestInit) => {
      abortSignal = init?.signal as AbortSignal;
      return Promise.resolve({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers({ 'Content-Type': 'text/event-stream' }),
        body: neverEndingStream,
      } as unknown as Response);
    }) as any;

    const sub = service.stream<any>('http://test/sse', { transport: 'fetch' }).subscribe();

    // Allow the fetch promise to resolve
    await vi.advanceTimersByTimeAsync(1);

    expect(abortSignal).toBeTruthy();
    expect(abortSignal!.aborted).toBe(false);

    sub.unsubscribe();

    expect(abortSignal!.aborted).toBe(true);
  });

  it('should error on non-200 response', (done: any) => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        body: null,
      } as unknown as Response)
    ) as any;

    service.stream<any>('http://test/sse', { transport: 'fetch' }).subscribe({
      next: () => done(new Error('should not emit')),
      error: (err: Error) => {
        expect(err.message).toContain('401');
        done();
      },
    });
  });

  it('should skip SSE comment lines', (done: any) => {
    const sseChunks = [
      ': this is a comment\ndata: {"v":1}\n\n',
    ];

    globalThis.fetch = vi.fn(() =>
      Promise.resolve(createMockResponse(sseChunks))
    ) as any;

    const sub = service.stream<{ v: number }>('http://test/sse', {
      transport: 'fetch',
    }).subscribe({
      next: (v) => {
        expect(v.v).toBe(1);
        sub.unsubscribe();
        done();
      },
      error: done,
    });
  });
});
