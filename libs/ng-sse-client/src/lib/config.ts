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
};

export const SSE_CLIENT_CONFIG = new InjectionToken<Partial<SseClientConfig>>(
  'SSE_CLIENT_CONFIG',
  {
    providedIn: 'root',
    factory: () => ({}),
  }
);
