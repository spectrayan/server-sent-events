export function computeBackoffDelay(
  attempt: number,
  initialDelayMs: number,
  maxDelayMs: number,
  multiplier: number,
  jitterRatio: number
): number {
  const pow = Math.max(0, attempt - 1);
  const base = Math.min(maxDelayMs, initialDelayMs * Math.pow(multiplier, pow));
  if (jitterRatio <= 0) return base;
  const jitter = base * jitterRatio;
  const min = Math.max(0, base - jitter);
  const max = base + jitter;
  return Math.floor(min + Math.random() * (max - min));
}
