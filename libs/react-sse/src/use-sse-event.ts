import { useMemo } from 'react';
import { useSseStream, UseSseOptions, UseSseResult } from './use-sse-stream';

export function useSseEvent<T = unknown>(
  url: string | null | undefined,
  eventName: string,
  options: UseSseOptions<T> = {}
): UseSseResult<T> {
  const mergedOptions = useMemo(
    () => ({
      ...options,
      events: [eventName],
    }),
    [options, eventName]
  );

  return useSseStream<T>(url, mergedOptions);
}
