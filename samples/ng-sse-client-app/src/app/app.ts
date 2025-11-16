import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiCallbackConfig, SseClient } from '@spectrayan-sse/ng-sse-client';
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

  // Simple in-memory notification model for the demo
  notifications: Array<{ id: string; message: string; read: boolean }> = [];
  bellOpen = false;

  private sub: Subscription | null = null;

  // Demo API callback configuration to mark notifications as read
  // Replace `url` with your backend endpoint as appropriate
  private markReadCallback: ApiCallbackConfig<{ id: string }> = {
    method: 'POST',
    url: 'http://localhost:8080/api/notifications/mark-read',
    transformPayload: (eventData) => ({ notificationId: eventData.id }),
    withCredentials: false,
    headers: { 'Content-Type': 'application/json' },
    timeout: 5000,
  };

  constructor(private sse: SseClient) {}

  get unreadCount(): number {
    return this.notifications.filter((n) => !n.read).length;
  }

  toggleBell() {
    this.bellOpen = !this.bellOpen;
  }

  markAsRead(n: { id: string; message: string; read: boolean }) {
    if (n.read) return;
    // Update UI state immediately; the server-side callback (if desired) is now
    // configured via SseClient callbacks in stream() and handled automatically.
    n.read = true;
  }

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
    this.notifications = [];
    this.connect(u.id);
  }

  private connect(userId: string) {
    // Tear down any previous connection
    this.sub?.unsubscribe();
    this.sub = null;

    // Update UI state
    this.status = 'connecting';

    // NOTE: The sample server exposes SSE at GET http://localhost:8080/sse/{userId}
    // It may emit named events: "message" (string) and "notification" (object)
    const url = `http://localhost:8080/sse/` + userId;

    this.sub = this.sse
      .stream<any>(url, {
        // Only include named events here; default 'message' is always handled by the client
        events: ['notification'],
        parse: this.parse,
        reconnection: { enabled: true, maxRetries: -1, initialDelayMs: 1000, maxDelayMs: 30000, backoffMultiplier: 2, jitterRatio: 0.2 },
        callbacks: [
          {
            eventType: 'notification',
            condition: (d: any) => !!(d && typeof d === 'object' && 'id' in d),
            apiCallback: this.markReadCallback,
            retry: { enabled: true, maxRetries: 3, delayMs: 1000 },
          },
        ],
      })
      .subscribe({
        next: (data) => {
          if (this.status !== 'connected') this.status = 'connected';

          // Append to raw messages list for visibility
          const msg = this.formatData(data);
          this.messages.unshift(msg);
          if (this.messages.length > 200) this.messages.pop();

          // If the payload looks like a notification, add it to the bell list
          if (data && typeof data === 'object' && 'id' in (data as any)) {
            const id = String((data as any)['id']);
            const message = (data as any)['message'] ?? (data as any)['title'] ?? msg;
            // Avoid duplicates by id
            if (!this.notifications.find((n) => n.id === id)) {
              this.notifications.unshift({ id, message: String(message), read: false });
              if (this.notifications.length > 100) this.notifications.pop();
            }
          }
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
