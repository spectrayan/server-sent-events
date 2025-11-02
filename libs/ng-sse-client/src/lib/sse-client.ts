import { Injectable, NgZone, inject } from '@angular/core';
import {
  DEFAULT_RECONNECTION_CONFIG,
  DEFAULT_SSE_CLIENT_CONFIG,
  SSE_CLIENT_CONFIG,
  SseClientConfig,
  SseReconnectionConfig,
} from './config';
import { EventSourceFactory, EventSourceLike } from './event-source.factory';
import { Observable } from 'rxjs';
import { computeBackoffDelay } from './backoff';

export interface StreamOptions<T = unknown> extends Partial<Omit<SseClientConfig, 'url' | 'parse'>> {
  /** Custom parser for the data payload */
  parse?: (data: string) => T;
}

@Injectable({ providedIn: 'root' })
export class SseClient {
  private readonly globalConfig: Partial<SseClientConfig>;
  private readonly zone = inject(NgZone);
  private readonly esFactory = inject(EventSourceFactory);

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

    return new Observable<T>((subscriber) => {
      let es: EventSourceLike | null = null;
      let closed = false;
      let retries = 0;
      let lastEventId: string | undefined;

      const openConnection = () => {
        const connectUrl = this.buildReconnectUrl(merged.url, merged.lastEventIdParamName, lastEventId);
        this.zone.runOutsideAngular(() => {
          try {
            es = this.esFactory.create(connectUrl, merged.withCredentials);
          } catch (err) {
            // EventSource might be unavailable; emit error and complete
            subscriber.error(err);
            return;
          }

          if (!es) return;

          // default message handler
          es.onmessage = (ev: MessageEvent) => {
            lastEventId = (ev as any).lastEventId as string | undefined;
            try {
              const data = merged.parse<T>(String(ev.data));
              this.zone.run(() => subscriber.next(data));
            } catch (e) {
              this.zone.run(() => subscriber.error(e));
            }
          };

          // onopen just resets retries
          es.onopen = () => {
            retries = 0;
          };

          const onError = (ev: Event) => {
            // If stream was intentionally closed, ignore
            if (closed) return;

            // If reconnection disabled, push error and complete
            if (!merged.reconnection.enabled) {
              this.zone.run(() => {
                subscriber.error(new Error('SSE connection error'));
              });
              return;
            }

            // Will attempt reconnects
            if (merged.reconnection.maxRetries >= 0 && retries >= merged.reconnection.maxRetries) {
              this.zone.run(() => subscriber.complete());
              return;
            }
            retries += 1;
            const delay = computeBackoffDelay(
              retries,
              merged.reconnection.initialDelayMs,
              merged.reconnection.maxDelayMs,
              merged.reconnection.backoffMultiplier,
              merged.reconnection.jitterRatio
            );

            // schedule reconnect
            setTimeout(() => {
              try {
                es?.close();
              } catch {}
              es = null;
              if (!closed) {
                openConnection();
              }
            }, delay);
          };

          es.onerror = onError;

          // attach named events if any
          const namedListeners: Array<{ name: string; fn: (ev: MessageEvent) => void }> = [];
          for (const name of merged.events) {
            const fn = (ev: MessageEvent) => {
              lastEventId = (ev as any).lastEventId as string | undefined;
              try {
                const data = merged.parse<T>(String(ev.data));
                this.zone.run(() => subscriber.next(data));
              } catch (e) {
                this.zone.run(() => subscriber.error(e));
              }
            };
            namedListeners.push({ name, fn });
            es.addEventListener(name, fn);
          }

          // On teardown remove listeners and close
          const teardown = () => {
            try {
              for (const l of namedListeners) {
                es?.removeEventListener(l.name, l.fn);
              }
              es?.close();
            } catch {}
            es = null;
          };

          // Register teardown once
          subscriber.add(() => {
            closed = true;
            teardown();
          });
        });
      };

      openConnection();

      return () => {
        closed = true;
        try {
          es?.close();
        } catch {}
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
      events: options?.events ?? this.globalConfig.events ?? DEFAULT_SSE_CLIENT_CONFIG.events,
      parse: (options?.parse as any) ?? this.globalConfig.parse ?? DEFAULT_SSE_CLIENT_CONFIG.parse,
      lastEventIdParamName:
        options?.lastEventIdParamName ?? this.globalConfig.lastEventIdParamName ?? 'lastEventId',
      reconnection,
    } as Required<SseClientConfig>;
  }
}
