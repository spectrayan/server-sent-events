import { NgZone } from '@angular/core';
import { Observable, Subscriber } from 'rxjs';
import { SseClientConfig, SseClientHooks, SseReconnectionConfig } from './config';
import { computeBackoffDelay } from './backoff';

/**
 * Parsed SSE frame from the `text/event-stream` protocol.
 * @see https://html.spec.whatwg.org/multipage/server-sent-events.html#parsing-an-event-stream
 */
interface SseFrame {
  event: string;
  data: string;
  id?: string;
  retry?: number;
}

/**
 * Creates an Observable SSE stream using `fetch()` + `ReadableStream`.
 *
 * Unlike the native `EventSource` API, this supports custom HTTP headers
 * (e.g. `Authorization: Bearer <token>`), making it suitable for
 * authenticated SSE endpoints.
 *
 * The SSE text protocol is parsed manually from the response body stream,
 * following the W3C/WHATWG specification.
 */
export function fetchStream<T>(
  config: Required<SseClientConfig>,
  zone: NgZone,
  processEventData: (data: T, eventType: string) => void,
): Observable<T> {
  return new Observable<T>((subscriber) => {
    let abortController: AbortController | null = null;
    let closed = false;
    let retries = 0;
    let lastEventId: string | undefined;

    const hooks: SseClientHooks = config.hooks ?? {};
    const recon: SseReconnectionConfig = config.reconnection;

    const openConnection = () => {
      if (closed) return;

      const connectUrl = buildReconnectUrl(config.url, config.lastEventIdParamName, lastEventId);

      // Hook: about to connect
      try { hooks.onConnect?.(connectUrl); } catch { /* noop */ }

      zone.runOutsideAngular(() => {
        abortController = new AbortController();
        const { signal } = abortController;

        const headers: Record<string, string> = {
          'Accept': 'text/event-stream',
          ...(config.headers ?? {}),
        };

        // Append Last-Event-ID header for resumption
        if (lastEventId) {
          headers['Last-Event-ID'] = lastEventId;
        }

        fetch(connectUrl, {
          method: 'GET',
          headers,
          credentials: config.withCredentials ? 'include' : 'same-origin',
          signal,
          cache: 'no-store',
        })
          .then((response) => {
            if (!response.ok) {
              throw new Error(`SSE fetch failed: ${response.status} ${response.statusText}`);
            }

            if (!response.body) {
              if (response.status === 204) {
                throw new Error(`SSE server returned HTTP 204 No Content — the endpoint exists but produced no body`);
              }
              throw new Error('SSE response has no body (ReadableStream not supported?)');
            }

            // Connection established — reset retries
            const attempt = retries;
            retries = 0;
            try { hooks.onOpen?.({ url: connectUrl, attempt }); } catch { /* noop */ }

            return readSseStream(response.body, subscriber, config, zone, processEventData, (id) => {
              lastEventId = id;
            }, signal, config.idleTimeoutMs);
          })
          .then(() => {
            // Stream ended normally (server closed the connection)
            if (!closed) {
              scheduleReconnect();
            }
          })
          .catch((err: Error) => {
            if (closed || signal.aborted) return;
            handleError(err);
          });
      });
    };

    const handleError = (err: Error) => {
      const event = new ErrorEvent('error', { message: err.message });

      if (!recon.enabled) {
        try { hooks.onError?.({ event, attempt: retries, willRetry: false }); } catch { /* noop */ }
        zone.run(() => subscriber.error(err));
        return;
      }

      if (recon.maxRetries >= 0 && retries >= recon.maxRetries) {
        try { hooks.onError?.({ event, attempt: retries, willRetry: false }); } catch { /* noop */ }
        try { hooks.onClose?.({ reason: 'retriesExceeded' }); } catch { /* noop */ }
        zone.run(() => subscriber.complete());
        return;
      }

      scheduleReconnect(event);
    };

    const scheduleReconnect = (errorEvent?: Event) => {
      if (closed) return;

      const nextAttempt = retries + 1;
      const delay = computeBackoffDelay(
        nextAttempt,
        recon.initialDelayMs,
        recon.maxDelayMs,
        recon.backoffMultiplier,
        recon.jitterRatio,
      );

      if (errorEvent) {
        try {
          hooks.onError?.({ event: errorEvent, attempt: nextAttempt, willRetry: true, nextDelayMs: delay });
          hooks.onReconnectAttempt?.({ attempt: nextAttempt, delayMs: delay });
        } catch { /* noop */ }
      }

      setTimeout(() => {
        retries = nextAttempt;
        try { abortController?.abort(); } catch { /* noop */ }
        abortController = null;
        if (!closed) {
          openConnection();
        }
      }, delay);
    };

    openConnection();

    // Teardown
    return () => {
      closed = true;
      try { hooks.onClose?.({ reason: 'unsubscribe' }); } catch { /* noop */ }
      try { abortController?.abort(); } catch { /* noop */ }
      abortController = null;
    };
  });
}

/**
 * Reads the SSE response body stream and dispatches parsed events.
 *
 * Implements the WHATWG EventStream parsing algorithm:
 * - Lines starting with `:` are comments (ignored)
 * - `data:` field accumulates (multi-line data joined with `\n`)
 * - `event:` sets the event type (default: `message`)
 * - `id:` sets the last event ID
 * - Empty line dispatches the accumulated event
 */
async function readSseStream<T>(
  body: ReadableStream<Uint8Array>,
  subscriber: Subscriber<T>,
  config: Required<SseClientConfig>,
  zone: NgZone,
  processEventData: (data: T, eventType: string) => void,
  onId: (id: string) => void,
  signal: AbortSignal,
  idleTimeoutMs?: number,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();

  // SSE frame state
  let eventType = '';
  let dataLines: string[] = [];
  let buffer = '';

  try {
    while (!signal.aborted) {
      // Idle timeout: if no data arrives within the configured window, abort.
      // This prevents the stream from hanging indefinitely on stalled TCP connections.
      let readPromise: Promise<ReadableStreamReadResult<Uint8Array>> = reader.read();
      if (idleTimeoutMs && idleTimeoutMs > 0) {
        const timeout = new Promise<never>((_, reject) =>
          setTimeout(() => reject(new Error(`SSE idle timeout: no data received for ${idleTimeoutMs}ms`)), idleTimeoutMs)
        );
        readPromise = Promise.race([readPromise, timeout]) as Promise<ReadableStreamReadResult<Uint8Array>>;
      }
      const { done, value } = await readPromise;
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      // Process complete lines
      const lines = buffer.split('\n');
      // Keep the last (possibly incomplete) line in the buffer
      buffer = lines.pop() ?? '';

      for (const rawLine of lines) {
        const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine;

        if (line === '') {
          // Empty line → dispatch event if we have data
          if (dataLines.length > 0) {
            const rawData = dataLines.join('\n');
            dataLines = [];
            const type = eventType || 'message';
            eventType = '';

            // Check if this event type is one we're listening for
            const listeningEvents = config.events ?? [];
            const shouldEmit = listeningEvents.length === 0 ||
              type === 'message' ||
              listeningEvents.includes(type);

            if (shouldEmit) {
              try {
                const parsed = config.parse<T>(rawData);
                processEventData(parsed, type);
                zone.run(() => subscriber.next(parsed));
              } catch (e) {
                zone.run(() => subscriber.error(e));
                return;
              }
            }
          }
          continue;
        }

        // Comment line
        if (line.startsWith(':')) continue;

        // Field parsing
        const colonIndex = line.indexOf(':');
        let field: string;
        let value: string;

        if (colonIndex === -1) {
          field = line;
          value = '';
        } else {
          field = line.slice(0, colonIndex);
          // Skip the optional space after colon
          value = line.charAt(colonIndex + 1) === ' '
            ? line.slice(colonIndex + 2)
            : line.slice(colonIndex + 1);
        }

        switch (field) {
          case 'data':
            dataLines.push(value);
            break;
          case 'event':
            eventType = value;
            break;
          case 'id':
            // Per spec, id must not contain null
            if (!value.includes('\0')) {
              onId(value);
            }
            break;
          case 'retry': {
            const ms = parseInt(value, 10);
            if (!isNaN(ms) && ms >= 0) {
              // Could adjust reconnection delay — for now we respect the configured backoff
            }
            break;
          }
          default:
            // Unknown field — ignore per spec
            break;
        }
      }
    }
  } finally {
    try { reader.cancel(); } catch { /* noop */ }
  }
}

function buildReconnectUrl(base: string, paramName: string, lastEventId?: string): string {
  if (!lastEventId) return base;
  try {
    const url = new URL(base, typeof window !== 'undefined' ? window.location?.origin : undefined);
    url.searchParams.set(paramName, lastEventId);
    return url.toString();
  } catch {
    const sep = base.includes('?') ? '&' : '?';
    return `${base}${sep}${encodeURIComponent(paramName)}=${encodeURIComponent(lastEventId)}`;
  }
}
