export interface SseEvent<T = unknown> {
  id?: string;
  event?: string;
  data: T;
  retry?: number;
  raw: string;
}

export interface SseReconnectionConfig {
  enabled: boolean;
  maxRetries: number;
  initialDelayMs: number;
  maxDelayMs: number;
  backoffMultiplier: number;
  jitterRatio: number;
}

export type ConnectionStatus = 'idle' | 'connecting' | 'open' | 'error' | 'closed';

export interface SseClientConfig {
  baseUrl?: string;
  headers?: Record<string, string> | (() => Promise<Record<string, string>> | Record<string, string>);
  withCredentials?: boolean;
  lastEventIdParamName?: string;
  reconnection?: Partial<SseReconnectionConfig>;
  fetch?: typeof fetch;
}

export interface StreamOptions<T = unknown> {
  parse?: (data: string) => T;
  events?: string[];
  headers?: Record<string, string>;
  lastEventIdParamName?: string;
  reconnection?: Partial<SseReconnectionConfig>;
  signal?: AbortSignal;
  onOpen?: (info: { url: string; attempt: number }) => void;
  onMessage?: (event: SseEvent<T>) => void;
  onError?: (info: { error: Error; attempt: number; willRetry: boolean; nextDelayMs?: number }) => void;
  onReconnectAttempt?: (info: { attempt: number; delayMs: number }) => void;
  onClose?: (reason: 'unsubscribe' | 'abort' | 'retriesExceeded') => void;
}

export interface SseObserver<T> {
  next?: (data: T) => void;
  error?: (err: Error) => void;
  complete?: () => void;
}

export interface SseSubscription<T = unknown> {
  unsubscribe(): void;
  readonly status: ConnectionStatus;
  [Symbol.asyncIterator](): AsyncIterator<T>;
}
