import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SseClient } from '@spectrayan-sse/ng-sse-client';
import { Subscription } from 'rxjs';

type SseStatus = 'connecting' | 'connected' | 'disconnected';

@Component({
  imports: [CommonModule],
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnDestroy {
  users = [
    { id: 'john', name: 'John Doe' },
    { id: 'alice', name: 'Alice' },
    { id: 'bob', name: 'Bob' },
  ];

  selectedUser: { id: string; name: string } | null = null;
  messages: string[] = [];
  status: SseStatus = 'disconnected';

  private sub: Subscription | null = null;

  constructor(private sse: SseClient) {}

  private formatData(data: unknown): string {
    if (typeof data === 'string') return data;
    try {
      return JSON.stringify(data);
    } catch {
      return String(data);
    }
  }

  private parse = <T>(d: string): T => {
    try {
      return JSON.parse(d) as T;
    } catch {
      return d as unknown as T;
    }
  };

  selectUser(u: { id: string; name: string }) {
    if (this.selectedUser?.id === u.id) return;
    this.selectedUser = u;
    this.messages = [];
    this.connect(u.id);
  }

  private connect(userId: string) {
    // Tear down any previous connection
    this.sub?.unsubscribe();
    this.sub = null;

    // Update UI state
    this.status = 'connecting';

    // NOTE: The sample server exposes SSE at GET http://localhost:8080/sse/{topic}
    // It emits named events: "message" (string) and "notification" (object),
    // plus an internal "heartbeat" which we don't subscribe to.
    // We'll subscribe to topic "notifications" so the scheduled demo events appear.
    const url = `http://localhost:8080/sse/` + userId;

    this.sub = this.sse
      .stream<any>(url, {
        events: ['message', 'notification'],
        parse: this.parse,
        reconnection: { enabled: true, maxRetries: -1, initialDelayMs: 1000, maxDelayMs: 30000, backoffMultiplier: 2, jitterRatio: 0.2 },
      })
      .subscribe({
        next: (data) => {
          if (this.status !== 'connected') this.status = 'connected';
          const msg = this.formatData(data);
          this.messages.unshift(msg);
          if (this.messages.length > 200) this.messages.pop();
        },
        error: () => {
          this.status = 'disconnected';
        },
        complete: () => {
          this.status = 'disconnected';
        },
      });
  }

  disconnect() {
    this.sub?.unsubscribe();
    this.sub = null;
    this.selectedUser = null;
    this.status = 'disconnected';
  }

  clearMessages() {
    this.messages = [];
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.sub = null;
  }
}
