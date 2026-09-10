import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { SseProvider, useSseStream, useSseEvent } from '../src/index';
import { createSseClient } from '@spectrayan/sse-client';

function createMockResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  let index = 0;

  const stream = new ReadableStream<Uint8Array>({
    pull(controller) {
      if (index < chunks.length) {
        controller.enqueue(encoder.encode(chunks[index++]));
      } else {
        controller.close();
      }
    },
  });

  return new Response(stream, {
    status: 200,
    statusText: 'OK',
    headers: { 'Content-Type': 'text/event-stream' },
  });
}

describe('React SSE Hooks', () => {
  it('should remain idle when url is null', () => {
    const { result } = renderHook(() => useSseStream(null));

    expect(result.current.status).toBe('idle');
    expect(result.current.data).toBeUndefined();
    expect(result.current.error).toBeNull();
  });

  it('should connect and receive data updates via useSseStream', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      createMockResponse(['data: {"count": 42}\n\n'])
    );
    const mockClient = createSseClient({ fetch: mockFetch as any });

    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <SseProvider client={mockClient}>{children}</SseProvider>
    );

    const { result } = renderHook(
      () =>
        useSseStream<{ count: number }>('http://localhost:8080/sse/test', {
          reconnection: { enabled: false },
        }),
      { wrapper }
    );

    // Wait for event to arrive
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });

    expect(result.current.data).toEqual({ count: 42 });
    expect(result.current.status).toBe('closed');
  });

  it('should filter named events using useSseEvent', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      createMockResponse([
        'event: ping\ndata: 1\n\n',
        'event: order\ndata: {"id": "ord-123"}\n\n',
      ])
    );
    const mockClient = createSseClient({ fetch: mockFetch as any });

    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <SseProvider client={mockClient}>{children}</SseProvider>
    );

    const { result } = renderHook(
      () =>
        useSseEvent<{ id: string }>('http://localhost:8080/sse/test', 'order', {
          reconnection: { enabled: false },
        }),
      { wrapper }
    );

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });

    expect(result.current.data).toEqual({ id: 'ord-123' });
  });

  it('should close connection when close() is invoked', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      createMockResponse(['data: keepalive\n\n'])
    );
    const mockClient = createSseClient({ fetch: mockFetch as any });

    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <SseProvider client={mockClient}>{children}</SseProvider>
    );

    const { result } = renderHook(
      () =>
        useSseStream('http://localhost:8080/sse/test', {
          reconnection: { enabled: false },
        }),
      { wrapper }
    );

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });

    act(() => {
      result.current.close();
    });

    expect(result.current.status).toBe('closed');
  });
});
