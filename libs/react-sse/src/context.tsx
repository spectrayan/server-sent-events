import React, { createContext, useContext, useMemo } from 'react';
import { createSseClient, SseClient, SseClientConfig } from '@spectrayan/sse-client';

interface SseContextValue {
  client: SseClient;
}

const SseContext = createContext<SseContextValue | null>(null);

export interface SseProviderProps {
  client?: SseClient;
  config?: SseClientConfig;
  children: React.ReactNode;
}

export function SseProvider({ client, config, children }: SseProviderProps) {
  const sseClient = useMemo(() => {
    if (client) return client;
    return createSseClient(config);
  }, [client, config]);

  const value = useMemo(() => ({ client: sseClient }), [sseClient]);

  return <SseContext.Provider value={value}>{children}</SseContext.Provider>;
}

export function useSseClient(): SseClient {
  const context = useContext(SseContext);
  if (!context) {
    // Return a singleton fallback if outside provider
    return defaultClient;
  }
  return context.client;
}

const defaultClient = createSseClient();
