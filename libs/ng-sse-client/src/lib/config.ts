import { InjectionToken } from '@angular/core';

export interface SseReconnectionConfig {
  enabled: boolean;
  /** Number of reconnection attempts. -1 for infinite. Default: -1 */
  maxRetries: number;
  /** Initial backoff delay in ms. Default: 1000 */
  initialDelayMs: number;
  /** Max backoff delay in ms. Default: 30000 */
  maxDelayMs: number;
  /** Multiplier for exponential backoff. Default: 2 */
  backoffMultiplier: number;
  /** Jitter ratio [0..1] to randomize delay. Default: 0.2 */
  jitterRatio: number;
}

export interface SseClientConfig {
  /** SSE endpoint URL */
  url: string;
  /** Whether to include credentials (cookies, auth) */
  withCredentials?: boolean;
  /** Custom event names to subscribe to in addition to the default "message" */
  events?: string[];
  /** Optional parser for incoming event data. Default: JSON.parse */
  parse?: <T>(data: string) => T;
  /** Name of the query parameter to send the lastEventId on reconnect. Default: 'lastEventId' */
  lastEventIdParamName?: string;
  /** Reconnection strategy configuration */
  reconnection?: SseReconnectionConfig;
    /** Event-triggered callback configurations */
    callbacks?: EventCallbackConfig[];
}

export interface ApiCallbackConfig<T = any> {
    /** HTTP method for the callback */
    method: 'POST' | 'PUT' | 'PATCH';
    /** Target URL for the API call */
    url: string;
    /** Function to transform SSE event data into API payload */
    transformPayload?: (eventData: T) => any;
    /** Additional headers for the API call */
    headers?: Record<string, string>;
    /** Whether to include credentials */
    withCredentials?: boolean;
    /** Timeout for the API call in milliseconds */
    timeout?: number;
}

export interface EventCallbackConfig<T = any> {
    /** Event type to listen for. If not specified, applies to all events */
    eventType?: string;
    /** Predicate function to determine if callback should be triggered */
    condition?: (eventData: T) => boolean;
    /** API configuration for the callback */
    apiCallback: ApiCallbackConfig<T>;
    /** Whether to retry failed API calls */
    retry?: {
        enabled: boolean;
        maxRetries: number;
        delayMs: number;
    };
}

export const DEFAULT_RECONNECTION_CONFIG: SseReconnectionConfig = {
  enabled: true,
  maxRetries: -1,
  initialDelayMs: 1000,
  maxDelayMs: 30000,
  backoffMultiplier: 2,
  jitterRatio: 0.2,
};

export const DEFAULT_SSE_CLIENT_CONFIG: Readonly<Required<Omit<SseClientConfig, 'url'>>> = {
  withCredentials: false,
  events: [],
  // eslint-disable-next-line @typescript-eslint/no-unsafe-return
  parse: (data: string) => JSON.parse(data),
  lastEventIdParamName: 'lastEventId',
  reconnection: DEFAULT_RECONNECTION_CONFIG,
    callbacks: [],
};

export const SSE_CLIENT_CONFIG = new InjectionToken<Partial<SseClientConfig>>(
  'SSE_CLIENT_CONFIG',
  {
    providedIn: 'root',
    factory: () => ({}),
  }
);
