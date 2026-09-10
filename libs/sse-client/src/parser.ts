import { SseEvent } from './types';

export class SseChunkParser {
  private buffer = '';
  private currentEvent: string | undefined;
  private currentData: string[] = [];
  private currentId: string | undefined;
  private currentRetry: number | undefined;

  public feed(chunk: string, onEvent: (event: SseEvent<string>) => void): void {
    this.buffer += chunk;
    const lines = this.buffer.split(/\r\n|\r|\n/);
    // Keep the last incomplete fragment in the buffer
    this.buffer = lines.pop() ?? '';

    for (const line of lines) {
      if (line.length === 0) {
        this.dispatch(onEvent);
        continue;
      }

      if (line.startsWith(':')) {
        // Comment line (heartbeat, keepalive, etc.) — discard
        continue;
      }

      const colonIndex = line.indexOf(':');
      let field: string;
      let value: string;

      if (colonIndex === -1) {
        field = line;
        value = '';
      } else {
        field = line.slice(0, colonIndex);
        value = line.slice(colonIndex + 1);
        if (value.startsWith(' ')) {
          value = value.slice(1);
        }
      }

      switch (field) {
        case 'event':
          this.currentEvent = value;
          break;
        case 'data':
          this.currentData.push(value);
          break;
        case 'id':
          this.currentId = value;
          break;
        case 'retry': {
          const retryMs = parseInt(value, 10);
          if (!isNaN(retryMs) && retryMs >= 0) {
            this.currentRetry = retryMs;
          }
          break;
        }
        default:
          // Ignore unknown fields per SSE spec
          break;
      }
    }
  }

  public flush(onEvent: (event: SseEvent<string>) => void): void {
    if (this.buffer.length > 0) {
      const line = this.buffer;
      this.buffer = '';
      if (!line.startsWith(':')) {
        const colonIndex = line.indexOf(':');
        if (colonIndex !== -1) {
          const field = line.slice(0, colonIndex);
          let value = line.slice(colonIndex + 1);
          if (value.startsWith(' ')) {
            value = value.slice(1);
          }
          if (field === 'data') {
            this.currentData.push(value);
          }
        }
      }
      this.dispatch(onEvent);
    }
  }

  public reset(): void {
    this.buffer = '';
    this.currentEvent = undefined;
    this.currentData = [];
    this.currentId = undefined;
    this.currentRetry = undefined;
  }

  private dispatch(onEvent: (event: SseEvent<string>) => void): void {
    if (this.currentData.length === 0 && !this.currentEvent && !this.currentId) {
      return;
    }

    const rawData = this.currentData.join('\n');
    const event: SseEvent<string> = {
      id: this.currentId,
      event: this.currentEvent,
      data: rawData,
      retry: this.currentRetry,
      raw: rawData,
    };

    // Reset current event accumulator (retaining id across events per spec)
    this.currentEvent = undefined;
    this.currentData = [];
    this.currentRetry = undefined;

    onEvent(event);
  }
}
