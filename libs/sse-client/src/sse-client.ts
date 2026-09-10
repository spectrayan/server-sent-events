import {
  ConnectionStatus,
  SseClientConfig,
  SseEvent,
  SseObserver,
  SseReconnectionConfig,
  SseSubscription,
  StreamOptions,
} from './types';
import { calculateBackoffDelay, DEFAULT_RECONNECTION_CONFIG } from './backoff';
import { SseChunkParser } from './parser';

export class SseClient {
  private config: SseClientConfig;

  constructor(config: SseClientConfig = {}) {
    this.config = { ...config };
  }

  public stream<T = string>(
    url: string,
    options: StreamOptions<T> = {},
    observer?: SseObserver<T>
  ): SseSubscription<T> {
    return this.createSubscription(url, options, observer);
  }

  public streamEvent<T = string>(
    url: string,
    eventName: string,
    options: StreamOptions<T> = {},
    observer?: SseObserver<T>
  ): SseSubscription<T> {
    return this.createSubscription(
      url,
      {
        ...options,
        events: [eventName],
      },
      observer
    );
  }

  public async *iterate<T = string>(
    url: string,
    options: StreamOptions<T> = {}
  ): AsyncIterable<T> {
    const queue: T[] = [];
    let resolveNext: ((value: IteratorResult<T>) => void) | null = null;
    let isDone = false;
    let streamError: Error | null = null;

    const sub = this.stream<T>(url, options, {
      next: (val) => {
        if (resolveNext) {
          const resolve = resolveNext;
          resolveNext = null;
          resolve({ value: val, done: false });
        } else {
          queue.push(val);
        }
      },
      error: (err) => {
        streamError = err;
        isDone = true;
        if (resolveNext) {
          const resolve = resolveNext;
          resolveNext = null;
          resolve({ value: undefined as any, done: true });
        }
      },
      complete: () => {
        isDone = true;
        if (resolveNext) {
          const resolve = resolveNext;
          resolveNext = null;
          resolve({ value: undefined as any, done: true });
        }
      },
    });

    try {
      while (!isDone || queue.length > 0) {
        if (queue.length > 0) {
          yield queue.shift()!;
        } else if (isDone) {
          break;
        } else {
          const next = await new Promise<IteratorResult<T>>((res) => {
            resolveNext = res;
          });
          if (next.done) {
            break;
          }
          yield next.value;
        }
      }
      if (streamError) {
        throw streamError;
      }
    } finally {
      sub.unsubscribe();
    }
  }

  private createSubscription<T>(
    url: string,
    options: StreamOptions<T>,
    initialObserver?: SseObserver<T>
  ): SseSubscription<T> {
    let status: ConnectionStatus = 'idle';
    let attempt = 0;
    let abortController: AbortController | null = null;
    let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
    let lastEventId: string | undefined;
    let isUnsubscribed = false;

    const observers = new Set<SseObserver<T>>();
    if (initialObserver) {
      observers.add(initialObserver);
    }

    const reconnection: SseReconnectionConfig = {
      ...DEFAULT_RECONNECTION_CONFIG,
      ...(this.config.reconnection || {}),
      ...(options.reconnection || {}),
    };

    const lastEventIdParam =
      options.lastEventIdParamName ||
      this.config.lastEventIdParamName ||
      'lastEventId';

    const parseFn =
      options.parse ||
      ((data: string) => {
        try {
          return JSON.parse(data) as T;
        } catch {
          return data as unknown as T;
        }
      });

    const notifyNext = (data: T) => {
      for (const obs of observers) {
        obs.next?.(data);
      }
    };

    const notifyError = (err: Error) => {
      for (const obs of observers) {
        obs.error?.(err);
      }
    };

    const notifyComplete = () => {
      for (const obs of observers) {
        obs.complete?.();
      }
    };

    const buildFullUrl = (): string => {
      let resolvedUrl = url;
      if (this.config.baseUrl && !url.startsWith('http://') && !url.startsWith('https://')) {
        const base = this.config.baseUrl.endsWith('/')
          ? this.config.baseUrl
          : `${this.config.baseUrl}/`;
        const path = url.startsWith('/') ? url.slice(1) : url;
        resolvedUrl = `${base}${path}`;
      }

      if (lastEventId) {
        const separator = resolvedUrl.includes('?') ? '&' : '?';
        resolvedUrl = `${resolvedUrl}${separator}${encodeURIComponent(
          lastEventIdParam
        )}=${encodeURIComponent(lastEventId)}`;
      }

      return resolvedUrl;
    };

    const connect = async () => {
      if (isUnsubscribed) return;

      attempt++;
      status = 'connecting';
      abortController = new AbortController();

      if (options.signal) {
        options.signal.addEventListener('abort', () => cleanup('abort'), { once: true });
      }

      try {
        const targetUrl = buildFullUrl();
        const headers: Record<string, string> = {
          Accept: 'text/event-stream',
          'Cache-Control': 'no-cache',
        };

        if (this.config.headers) {
          const configHeaders =
            typeof this.config.headers === 'function'
              ? await this.config.headers()
              : this.config.headers;
          Object.assign(headers, configHeaders);
        }
        if (options.headers) {
          Object.assign(headers, options.headers);
        }
        if (lastEventId) {
          headers['Last-Event-ID'] = lastEventId;
        }

        const fetchImpl = this.config.fetch || globalThis.fetch;
        if (!fetchImpl) {
          throw new Error('fetch is not available in the current environment');
        }

        const response = await fetchImpl(targetUrl, {
          method: 'GET',
          headers,
          signal: abortController.signal,
          credentials: this.config.withCredentials ? 'include' : 'same-origin',
        });

        if (!response.ok) {
          throw new Error(`SSE HTTP error: ${response.status} ${response.statusText}`);
        }

        if (!response.body) {
          throw new Error('ReadableStream body is not supported by fetch response');
        }

        status = 'open';
        attempt = 0; // Reset attempts on successful connection
        options.onOpen?.({ url: targetUrl, attempt });

        const parser = new SseChunkParser();
        const reader = response.body.getReader();
        const decoder = new TextDecoder();

        while (true) {
          const { done, value } = await reader.read();
          if (done) {
            parser.flush((rawEvent) => dispatchEvent(rawEvent));
            break;
          }

          const textChunk = decoder.decode(value, { stream: true });
          parser.feed(textChunk, (rawEvent) => dispatchEvent(rawEvent));
        }

        // Clean close from server
        if (!isUnsubscribed) {
          if (reconnection.enabled) {
            handleDisconnect(new Error('SSE server closed connection'));
          } else {
            status = 'closed';
            options.onClose?.('unsubscribe');
            notifyComplete();
          }
        }
      } catch (err: any) {
        if (isUnsubscribed || abortController?.signal.aborted) {
          return;
        }
        handleDisconnect(err instanceof Error ? err : new Error(String(err)));
      }
    };

    const dispatchEvent = (rawEvent: SseEvent<string>) => {
      if (rawEvent.id) {
        lastEventId = rawEvent.id;
      }

      // If specific named events are filtered, only dispatch if match
      if (options.events && options.events.length > 0) {
        const eventName = rawEvent.event || 'message';
        if (!options.events.includes(eventName)) {
          return;
        }
      }

      try {
        const parsedData = parseFn(rawEvent.data);
        const typedEvent: SseEvent<T> = {
          ...rawEvent,
          data: parsedData,
        };
        options.onMessage?.(typedEvent);
        notifyNext(parsedData);
      } catch (parseError: any) {
        notifyError(
          new Error(`Failed to parse SSE event data: ${parseError.message}`)
        );
      }
    };

    const handleDisconnect = (error: Error) => {
      status = 'error';
      const nextDelay = calculateBackoffDelay(attempt + 1, reconnection);
      const willRetry = reconnection.enabled && nextDelay >= 0;

      options.onError?.({
        error,
        attempt: attempt + 1,
        willRetry,
        nextDelayMs: willRetry ? nextDelay : undefined,
      });

      if (willRetry) {
        options.onReconnectAttempt?.({ attempt: attempt + 1, delayMs: nextDelay });
        reconnectTimeout = setTimeout(() => {
          connect();
        }, nextDelay);
      } else {
        status = 'closed';
        options.onClose?.('retriesExceeded');
        notifyError(error);
      }
    };

    const cleanup = (reason: 'unsubscribe' | 'abort' | 'retriesExceeded' = 'unsubscribe') => {
      if (isUnsubscribed) return;
      isUnsubscribed = true;
      status = 'closed';

      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
      }

      if (abortController) {
        abortController.abort();
        abortController = null;
      }

      options.onClose?.(reason);
      observers.clear();
    };

    // Kick off connection
    connect();

    const subscription: SseSubscription<T> = {
      unsubscribe: () => cleanup('unsubscribe'),
      get status() {
        return status;
      },
      [Symbol.asyncIterator]: () => {
        return this.iterate(url, options)[Symbol.asyncIterator]();
      },
    };

    return subscription;
  }
}

export function createSseClient(config?: SseClientConfig): SseClient {
  return new SseClient(config);
}
