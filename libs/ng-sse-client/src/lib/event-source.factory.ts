import { Injectable, inject } from '@angular/core';
import { SSE_CLIENT_CONFIG, SseClientConfig } from './config';

export interface EventSourceLike {
  readonly url: string;
  readonly readyState: number;
  readonly withCredentials: boolean;
  onopen: ((this: EventSourceLike, ev: MessageEvent) => any) | null;
  onmessage: ((this: EventSourceLike, ev: MessageEvent) => any) | null;
  onerror: ((this: EventSourceLike, ev: Event) => any) | null;
  addEventListener(type: string, listener: (ev: MessageEvent) => any): void;
  removeEventListener(type: string, listener: (ev: MessageEvent) => any): void;
  close(): void;
}

export type EventSourceCtor = new (url: string, config?: { withCredentials?: boolean }) => EventSourceLike;

@Injectable({ providedIn: 'root' })
export class EventSourceFactory {
  private readonly defaultConfig = inject(SSE_CLIENT_CONFIG, { optional: true }) as Partial<SseClientConfig> | null;

  get ctor(): EventSourceCtor {
    // If running in a browser environment, window.EventSource should exist.
    const es: any = typeof window !== 'undefined' ? (window as any).EventSource : undefined;
    if (!es) {
      throw new Error('EventSource is not available in this environment. Are you running in the browser?');
    }
    return es as EventSourceCtor;
  }

  create(url: string, withCredentials?: boolean): EventSourceLike {
    const Ctor = this.ctor;
    return new Ctor(url, { withCredentials: withCredentials ?? this.defaultConfig?.withCredentials });
  }
}
