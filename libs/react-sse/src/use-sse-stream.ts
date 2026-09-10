import { useEffect, useState, useCallback, useRef } from 'react';
import { ConnectionStatus, SseSubscription, StreamOptions } from '@spectrayan/sse-client';
import { useSseClient } from './context';

export interface UseSseOptions<T = unknown> extends StreamOptions<T> {
  enabled?: boolean;
  initialData?: T;
}

export interface UseSseResult<T = unknown> {
  data: T | undefined;
  status: ConnectionStatus;
  error: Error | null;
  reconnect: () => void;
  close: () => void;
}

export function useSseStream<T = unknown>(
  url: string | null | undefined,
  options: UseSseOptions<T> = {}
): UseSseResult<T> {
  const client = useSseClient();
  const [data, setData] = useState<T | undefined>(options.initialData);
  const [status, setStatus] = useState<ConnectionStatus>('idle');
  const [error, setError] = useState<Error | null>(null);

  const subRef = useRef<SseSubscription<T> | null>(null);
  const optionsRef = useRef(options);
  optionsRef.current = options;

  const close = useCallback(() => {
    if (subRef.current) {
      subRef.current.unsubscribe();
      subRef.current = null;
      setStatus('closed');
    }
  }, []);

  const connect = useCallback(() => {
    close();

    if (!url || optionsRef.current.enabled === false) {
      setStatus('idle');
      return;
    }

    setStatus('connecting');
    setError(null);

    const subscription = client.stream<T>(
      url,
      {
        ...optionsRef.current,
        onOpen: (info) => {
          setStatus('open');
          optionsRef.current.onOpen?.(info);
        },
        onError: (info) => {
          setStatus('error');
          setError(info.error);
          optionsRef.current.onError?.(info);
        },
        onClose: (reason) => {
          setStatus('closed');
          optionsRef.current.onClose?.(reason);
        },
      },
      {
        next: (incoming) => {
          setData(incoming);
          setError(null);
        },
        error: (err) => {
          setError(err);
          setStatus('error');
        },
        complete: () => {
          setStatus('closed');
        },
      }
    );

    subRef.current = subscription;
  }, [client, url, close]);

  const reconnect = useCallback(() => {
    connect();
  }, [connect]);

  useEffect(() => {
    connect();
    return () => {
      close();
    };
  }, [connect, close]);

  return {
    data,
    status,
    error,
    reconnect,
    close,
  };
}
