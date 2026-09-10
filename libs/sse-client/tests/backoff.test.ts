import { describe, it, expect } from 'vitest';
import { calculateBackoffDelay, DEFAULT_RECONNECTION_CONFIG } from '../src/backoff';

describe('calculateBackoffDelay', () => {
  it('should return initialDelay on attempt 1 without jitter', () => {
    const delay = calculateBackoffDelay(1, DEFAULT_RECONNECTION_CONFIG, 0.5); // random = 0.5 -> jitter = 0
    expect(delay).toBe(1000);
  });

  it('should apply exponential multiplier on subsequent attempts', () => {
    const delay2 = calculateBackoffDelay(2, DEFAULT_RECONNECTION_CONFIG, 0.5);
    expect(delay2).toBe(2000);

    const delay3 = calculateBackoffDelay(3, DEFAULT_RECONNECTION_CONFIG, 0.5);
    expect(delay3).toBe(4000);

    const delay4 = calculateBackoffDelay(4, DEFAULT_RECONNECTION_CONFIG, 0.5);
    expect(delay4).toBe(8000);
  });

  it('should respect maxDelayMs ceiling', () => {
    const delay = calculateBackoffDelay(10, DEFAULT_RECONNECTION_CONFIG, 0.5);
    expect(delay).toBe(30000);
  });

  it('should return -1 when disabled', () => {
    const delay = calculateBackoffDelay(1, { ...DEFAULT_RECONNECTION_CONFIG, enabled: false });
    expect(delay).toBe(-1);
  });

  it('should return -1 when attempt exceeds maxRetries', () => {
    const delay = calculateBackoffDelay(4, { ...DEFAULT_RECONNECTION_CONFIG, maxRetries: 3 });
    expect(delay).toBe(-1);
  });

  it('should apply jitter within bounds', () => {
    const minJitter = calculateBackoffDelay(1, DEFAULT_RECONNECTION_CONFIG, 0.0); // -20%
    const maxJitter = calculateBackoffDelay(1, DEFAULT_RECONNECTION_CONFIG, 1.0); // +20%
    expect(minJitter).toBe(800);
    expect(maxJitter).toBe(1200);
  });
});
