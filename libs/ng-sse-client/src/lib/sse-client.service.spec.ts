import { TestBed } from '@angular/core/testing';
import { SseClient } from './sse-client';
import { SSE_CLIENT_CONFIG, SseClientConfig } from './config';

// Simple EventSource mock compatible with EventSourceLike
class MockEventSource {
  static instances: MockEventSource[] = [];

  url: string;
  withCredentials: boolean;
  readyState = 0;

  onopen: ((this: MockEventSource, ev: MessageEvent) => any) | null = null;
  onmessage: ((this: MockEventSource, ev: MessageEvent) => any) | null = null;
  onerror: ((this: MockEventSource, ev: Event) => any) | null = null;

  private listeners = new Map<string, Set<(ev: MessageEvent) => any>>();
  public closed = false;

  constructor(url: string, config?: { withCredentials?: boolean }) {
    this.url = url;
    this.withCredentials = !!config?.withCredentials;
    MockEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (ev: MessageEvent) => any): void {
    if (!this.listeners.has(type)) this.listeners.set(type, new Set());
    this.listeners.get(type)!.add(listener);
  }

  removeEventListener(type: string, listener: (ev: MessageEvent) => any): void {
    this.listeners.get(type)?.delete(listener);
  }

  emitOpen() {
    this.onopen?.(new MessageEvent('open'));
  }

  emitMessage(data: any, eventId?: string) {
    // Use a plain object shaped like MessageEvent to avoid read-only lastEventId on JSDOM's MessageEvent
    const ev = { data, lastEventId: eventId } as unknown as MessageEvent;
    this.onmessage?.(ev);
  }

  emitNamed(eventName: string, data: any, eventId?: string) {
    const ev = { data, lastEventId: eventId } as unknown as MessageEvent;
    this.listeners.get(eventName)?.forEach((l) => l(ev));
  }

  emitError() {
    this.onerror?.(new Event('error'));
  }

  close(): void {
    this.closed = true;
  }
}

describe('SseClient', () => {
  let service: SseClient;
  const originalEventSource = (global as any).EventSource;

  beforeEach(() => {
    jest.useFakeTimers();
    MockEventSource.instances = [];
    (global as any).EventSource = MockEventSource as any;

    TestBed.configureTestingModule({
      providers: [
        SseClient,
        {
          provide: SSE_CLIENT_CONFIG,
          useValue: {
            reconnection: { enabled: true, initialDelayMs: 10, maxDelayMs: 20, backoffMultiplier: 1, jitterRatio: 0, maxRetries: 3 },
            withCredentials: true,
          } satisfies Partial<SseClientConfig>,
        },
      ],
    });

    service = TestBed.inject(SseClient);
  });

  afterEach(() => {
    (global as any).EventSource = originalEventSource;
    jest.useRealTimers();
  });

  it('should emit parsed JSON from default message events', (done) => {
    const url = 'http://test/sse';
    const values: any[] = [];

    const sub = service.stream<{ a: number }>(url).subscribe({
      next: (v) => {
        values.push(v);
        expect(v.a).toBe(1);
        sub.unsubscribe();
        done();
      },
      error: done,
    });

    const es = MockEventSource.instances[0];
    expect(es).toBeTruthy();
    es.emitOpen();
    es.emitMessage(JSON.stringify({ a: 1 }), 'id-1');
  });

  it('should emit parsed JSON from a named event', (done) => {
    const url = 'http://test/sse';
    const sub = service.streamEvent<{ n: string }>(url, 'notice').subscribe({
      next: (v) => {
        expect(v.n).toBe('hello');
        sub.unsubscribe();
        done();
      },
      error: done,
    });

    const es = MockEventSource.instances[0];
    es.emitOpen();
    es.emitNamed('notice', JSON.stringify({ n: 'hello' }), 'e-1');
  });

  it('should attempt reconnection on error and continue streaming', (done) => {
    const url = 'http://test/sse';
    const results: number[] = [];

    const sub = service.stream<{ v: number }>(url).subscribe({
      next: (v) => {
        results.push(v.v);
        if (results.length === 2) {
          expect(MockEventSource.instances.length).toBeGreaterThan(1);
          sub.unsubscribe();
          done();
        }
      },
      error: done,
    });

    // first connection
    const es1 = MockEventSource.instances[0];
    es1.emitOpen();
    es1.emitMessage(JSON.stringify({ v: 1 }), 'id-1');

    // trigger error -> schedule reconnect (10ms per config)
    es1.emitError();

    // Fast-forward timers to trigger reconnect
    jest.advanceTimersByTime(10);

    const es2 = MockEventSource.instances[1];
    expect(es2).toBeTruthy();
    es2.emitOpen();
    es2.emitMessage(JSON.stringify({ v: 2 }), 'id-2');
  });

  it('should close EventSource on unsubscribe', () => {
    const url = 'http://test/sse';
    const sub = service.stream<any>(url).subscribe();
    const es = MockEventSource.instances[0];
    expect(es.closed).toBe(false);
    sub.unsubscribe();
    expect(es.closed).toBe(true);
  });
});
