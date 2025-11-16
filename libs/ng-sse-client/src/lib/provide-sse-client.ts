import { EnvironmentProviders, Provider, makeEnvironmentProviders } from '@angular/core';
import { SSE_CLIENT_CONFIG, SseClientConfig } from './config';

/**
 * Provide global configuration for the SSE client.
 *
 * Usage in `app.config.ts`:
 * ```ts
 * import { provideSseClient } from '@spectrayan-sse/ng-sse-client';
 *
 * export const appConfig: ApplicationConfig = {
 *   providers: [
 *     provideSseClient({
 *       withCredentials: true,
 *       hooks: { onConnect: url => console.log('[SSE] connecting', url) },
 *     }),
 *   ],
 * };
 * ```
 */
export function provideSseClient(config: Partial<SseClientConfig> = {}): EnvironmentProviders {
  // We purposefully return EnvironmentProviders to match Angular built-in `provideX()` helpers
  // and allow usage at both application and feature levels.
  const providers: Provider[] = [
    { provide: SSE_CLIENT_CONFIG, useValue: config },
  ];
  return makeEnvironmentProviders(providers);
}
