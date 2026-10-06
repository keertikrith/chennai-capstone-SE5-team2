import { HttpClient } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { AuthService } from '../auth/auth.service';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { NotificationItem } from '../models/notification.model';
import { AccountContextService } from './account-context.service';

/** The customer's notification history, shown as an inbox. Read-only apart from marking a message read. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly accountContext = inject(AccountContextService);
  private readonly auth = inject(AuthService);

  private readonly unreadSignal = signal(0);
  /** How many messages are unread, for the badge in the header. Updated by the calls below. */
  readonly unread = this.unreadSignal.asReadonly();

  constructor() {
    // Signing out forgets the count, so the next customer never sees the last one's badge.
    effect(() => {
      if (!this.auth.isAuthenticated()) {
        this.unreadSignal.set(0);
      }
    });
  }

  private base(accountId: number): string {
    return `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/notifications`;
  }

  list(accountId: number, unreadOnly = false, limit = 50): Observable<NotificationItem[]> {
    return this.http
      .get<NotificationItem[]>(this.base(accountId), { params: { unreadOnly, limit } })
      .pipe(tap((items) => {
        // Only the unfiltered list tells us the whole unread count.
        if (!unreadOnly && items.length < limit) {
          this.unreadSignal.set(items.filter((item) => !item.read).length);
        }
      }));
  }

  markRead(accountId: number, notificationId: number): Observable<NotificationItem> {
    return this.http
      .post<NotificationItem>(`${this.base(accountId)}/${notificationId}/read`, null)
      .pipe(tap(() => this.unreadSignal.update((count) => Math.max(0, count - 1))));
  }

  /** Re-reads the unread count. A failure leaves the badge as it was: a badge must never break a page. */
  refreshUnread(): Observable<number> {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    if (accountId <= 0) {
      return of(0);
    }
    return this.http.get<{ unread: number }>(`${this.base(accountId)}/unread-count`).pipe(
      map((result) => result.unread),
      tap((count) => this.unreadSignal.set(count)),
      catchError(() => of(this.unreadSignal()))
    );
  }

  /** Forgets the count, e.g. on sign-out, so the next customer never sees the last one's badge. */
  clear(): void {
    this.unreadSignal.set(0);
  }
}
