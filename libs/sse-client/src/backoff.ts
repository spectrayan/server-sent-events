import { SseReconnectionConfig } from './types';

export const DEFAULT_RECONNECTION_CONFIG: SseReconnectionConfig = {
  enabled: true,
  maxRetries: -1,
  initialDelayMs: 1000,
  maxDelayMs: 30000,
  backoffMultiplier: 2.0,
  jitterRatio: 0.2,
};

export function calculateBackoffDelay(
  attempt: number,
  config: SseReconnectionConfig = DEFAULT_RECONNECTION_CONFIG,
  randomValue = Math.random()
): number {
  if (!config.enabled) {
    return -1;
  }
  if (config.maxRetries >= 0 && attempt > config.maxRetries) {
    return -1;
  }

  const baseDelay = Math.min(
    config.initialDelayMs * Math.pow(config.backoffMultiplier, Math.max(0, attempt - 1)),
    config.maxDelayMs
  );

  const jitterFactor = 1 + (randomValue * 2 - 1) * config.jitterRatio;
  return Math.max(0, Math.round(baseDelay * jitterFactor));
}
