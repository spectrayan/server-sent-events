import { Injectable, NgZone, inject } from '@angular/core';
import {
  DEFAULT_RECONNECTION_CONFIG,
  DEFAULT_SSE_CLIENT_CONFIG,
  SSE_CLIENT_CONFIG,
  SseClientConfig,
  SseReconnectionConfig,
  SseTransport,
} from './config';
import { EventSourceFactory, EventSourceLike } from './event-source.factory';
import { Observable } from 'rxjs';
import { computeBackoffDelay } from './backoff';
import { ApiCallbackService } from './api-callback.service';
import { fetchStream } from './fetch-stream';

export interface StreamOptions<T = unknown> extends Partial<Omit<SseClientConfig, 'url' | 'parse'>> {
  /** Custom parser for the data payload */
  parse?: (data: string) => T;
  /** Transport: 'eventsource' (default) or 'fetch' (supports headers) */
  transport?: SseTransport;
  /** Custom headers — only used with transport: 'fetch' */
  headers?: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class SseClient {
  private readonly globalConfig: Partial<SseClientConfig>;
  private readonly zone = inject(NgZone);
  private readonly esFactory = inject(EventSourceFactory);
  private readonly apiCallback = inject(ApiCallbackService);

  constructor() {
    this.globalConfig = inject(SSE_CLIENT_CONFIG, { optional: true }) ?? {};
  }

  /**
   * Stream default `message` events from the SSE endpoint as typed values.
   */
  stream<T>(url: string, options?: StreamOptions<T>): Observable<T> {
    const events = options?.events ?? [];
    return this.streamInternal<T>(url, { ...options, events });
  }

  /**
   * Stream a specific named event from the SSE endpoint.
   */
  streamEvent<T>(url: string, event: string, options?: StreamOptions<T>): Observable<T> {
    return this.streamInternal<T>(url, { ...options, events: [event] });
  }

  private streamInternal<T>(url: string, options?: StreamOptions<T>): Observable<T> {
    const merged = this.mergeConfig<T>(url, options);

    // ── Fetch transport ─────────────────────────────────────────────
    if (merged.transport === 'fetch') {
      return fetchStream<T>(
        merged,
        this.zone,
        (data: T, eventType: string) => this.processEventDataShared(data, eventType, merged),
      );
    }

    // ── EventSource transport (default) ─────────────────────────────
    return this.streamEventSource<T>(merged);
  }

  /**
   * Shared event processing: fires hooks, executes callbacks, emits to subscriber.
   * Called by both EventSource and fetch transports.
   */
  private processEventDataShared<T>(data: T, eventType: string, merged: Required<SseClientConfig>): void {
    const hooks = merged.hooks ?? {};

    // Fire onMessage hook
    try {
      hooks.onMessage?.({ eventType, data, rawEvent: undefined as unknown as MessageEvent });
    } catch { /* noop */ }

    // Execute callbacks if configured
    const callbacks = merged.callbacks || [];
    for (const callbackConfig of callbacks) {
      if (callbackConfig.eventType && callbackConfig.eventType !== eventType) continue;
      if (callbackConfig.condition && !callbackConfig.condition(data)) continue;

      this.zone.runOutsideAngular(() => {
        this.apiCallback
          .executeCallbackWithRetry(data, callbackConfig.apiCallback, callbackConfig.retry)
          .subscribe({
            next: () => { /* side-effect success */ },
            error: () => { /* callback failure should not break the stream */ },
          });
      });
    }
  }

  /**
   * EventSource-based streaming (original implementation, unchanged).
   */
  private streamEventSource<T>(merged: Required<SseClientConfig>): Observable<T> {
    return new Observable<T>((subscriber) => {
      let es: EventSourceLike | null = null;
      let closed = false;
      let retries = 0;
      let lastEventId: string | undefined;

      const hooks = merged.hooks ?? {};

      const processEventData = (data: T, eventType: string, raw?: MessageEvent) => {
        try {
          hooks.onMessage?.({ eventType, data, rawEvent: raw as MessageEvent });
        } catch { /* noop */ }

        const callbacks = merged.callbacks || [];
        for (const callbackConfig of callbacks) {
          if (callbackConfig.eventType && callbackConfig.eventType !== eventType) continue;
          if (callbackConfig.condition && !callbackConfig.condition(data)) continue;

          this.zone.runOutsideAngular(() => {
            this.apiCallback
              .executeCallbackWithRetry(data, callbackConfig.apiCallback, callbackConfig.retry)
              .subscribe({
                next: () => { /* side-effect success */ },
                error: () => { /* callback failure should not break stream */ },
              });
          });
        }

        this.zone.run(() => subscriber.next(data));
      };

      const openConnection = () => {
        const connectUrl = this.buildReconnectUrl(merged.url, merged.lastEventIdParamName, lastEventId);

        try { hooks.onConnect?.(connectUrl); } catch { /* noop */ }

        this.zone.runOutsideAngular(() => {
          try {
            es = this.esFactory.create(connectUrl, merged.withCredentials);
          } catch (err) {
            subscriber.error(err);
            return;
          }

          if (!es) return;

          es.onmessage = (ev: MessageEvent) => {
            lastEventId = (ev as any).lastEventId as string | undefined;
            try {
              const data = merged.parse<T>(String(ev.data));
              processEventData(data, 'message', ev);
            } catch (e) {
              this.zone.run(() => subscriber.error(e));
            }
          };

          es.onopen = () => {
            const attempt = retries;
            retries = 0;
            try { hooks.onOpen?.({ url: connectUrl, attempt }); } catch { /* noop */ }
          };

          const onError = (ev: Event) => {
            if (closed) return;

            if (!merged.reconnection.enabled) {
              try { hooks.onError?.({ event: ev, attempt: retries, willRetry: false }); } catch { /* noop */ }
              this.zone.run(() => {
                subscriber.error(new Error('SSE connection error'));
              });
              return;
            }

            if (merged.reconnection.maxRetries >= 0 && retries >= merged.reconnection.maxRetries) {
              try { hooks.onError?.({ event: ev, attempt: retries, willRetry: false }); } catch { /* noop */ }
              try { hooks.onClose?.({ reason: 'retriesExceeded' }); } catch { /* noop */ }
              this.zone.run(() => subscriber.complete());
              return;
            }
            const nextAttempt = retries + 1;
            const delay = computeBackoffDelay(
              nextAttempt,
              merged.reconnection.initialDelayMs,
              merged.reconnection.maxDelayMs,
              merged.reconnection.backoffMultiplier,
              merged.reconnection.jitterRatio
            );

            try {
              hooks.onError?.({ event: ev, attempt: nextAttempt, willRetry: true, nextDelayMs: delay });
              hooks.onReconnectAttempt?.({ attempt: nextAttempt, delayMs: delay });
            } catch { /* noop */ }

            setTimeout(() => {
              retries = nextAttempt;
              try {
                es?.close();
              } catch { /* noop */ }
              es = null;
              if (!closed) {
                openConnection();
              }
            }, delay);
          };

          es.onerror = onError;

          const namedListeners: Array<{ name: string; fn: (ev: MessageEvent) => void }> = [];
          const uniqueNamedEvents = Array.from(new Set(merged.events)).filter((e) => e && e !== 'message');
          for (const name of uniqueNamedEvents) {
            const fn = (ev: MessageEvent) => {
              lastEventId = (ev as any).lastEventId as string | undefined;
              try {
                const data = merged.parse<T>(String(ev.data));
                processEventData(data, name, ev);
              } catch (e) {
                this.zone.run(() => subscriber.error(e));
              }
            };
            namedListeners.push({ name, fn });
            es.addEventListener(name, fn);
          }

          const teardown = () => {
            try {
              for (const l of namedListeners) {
                es?.removeEventListener(l.name, l.fn);
              }
              es?.close();
            } catch { /* noop */ }
            es = null;
          };

          subscriber.add(() => {
            closed = true;
            try { hooks.onClose?.({ reason: 'unsubscribe' }); } catch { /* noop */ }
            teardown();
          });
        });
      };

      openConnection();

      return () => {
        closed = true;
        try {
          es?.close();
        } catch { /* noop */ }
        es = null;
      };
    });
  }

  private buildReconnectUrl(base: string, paramName: string, lastEventId?: string): string {
    if (!lastEventId) return base;
    try {
      const url = new URL(base, typeof window !== 'undefined' ? window.location?.origin : undefined);
      url.searchParams.set(paramName, lastEventId);
      return url.toString();
    } catch {
      // Fallback: naive append
      const sep = base.includes('?') ? '&' : '?';
      return `${base}${sep}${encodeURIComponent(paramName)}=${encodeURIComponent(lastEventId)}`;
    }
  }

  private mergeConfig<T>(url: string, options?: StreamOptions<T>): Required<SseClientConfig> {
    const reconnection: SseReconnectionConfig = {
      ...DEFAULT_RECONNECTION_CONFIG,
      ...(this.globalConfig.reconnection ?? {}),
      ...(options?.reconnection ?? {}),
    };

    return {
      url,
      withCredentials: options?.withCredentials ?? this.globalConfig.withCredentials ?? DEFAULT_SSE_CLIENT_CONFIG.withCredentials,
      transport: options?.transport ?? this.globalConfig.transport ?? DEFAULT_SSE_CLIENT_CONFIG.transport,
      headers: { ...(this.globalConfig.headers ?? {}), ...(options?.headers ?? {}) },
      events: options?.events ?? this.globalConfig.events ?? DEFAULT_SSE_CLIENT_CONFIG.events,
      parse: (options?.parse as any) ?? this.globalConfig.parse ?? DEFAULT_SSE_CLIENT_CONFIG.parse,
      lastEventIdParamName:
        options?.lastEventIdParamName ?? this.globalConfig.lastEventIdParamName ?? 'lastEventId',
      reconnection,
      callbacks: options?.callbacks ?? this.globalConfig.callbacks ?? DEFAULT_SSE_CLIENT_CONFIG.callbacks,
      hooks: options?.hooks ?? this.globalConfig.hooks ?? DEFAULT_SSE_CLIENT_CONFIG.hooks,
      idleTimeoutMs: options?.idleTimeoutMs ?? this.globalConfig.idleTimeoutMs ?? DEFAULT_SSE_CLIENT_CONFIG.idleTimeoutMs,
      logger: options?.logger ?? this.globalConfig.logger ?? DEFAULT_SSE_CLIENT_CONFIG.logger,
    } as Required<SseClientConfig>;
  }
}
