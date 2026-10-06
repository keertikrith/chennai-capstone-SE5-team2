import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { NotificationItem, NotificationKind } from '../../../core/models/notification.model';
import { AlertChannel } from '../../../core/models/preferences.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { NotificationService } from '../../../core/services/notification.service';

/** How often the inbox looks for new messages. */
export const INBOX_REFRESH_MS = 30_000;

type Filter = 'ALL' | 'UNREAD';

const KIND_LABELS: Record<NotificationKind, string> = {
  ORDER_FILLED: 'Order filled',
  ORDER_REJECTED: 'Order rejected',
  ORDER_CANCELLED: 'Order cancelled',
  PRICE_ALERT: 'Price alert'
};

const CHANNEL_LABELS: Record<AlertChannel, string> = {
  EMAIL: 'e-mail',
  SMS: 'text message',
  PUSH: 'in-app notification'
};

/**
 * The customer's notification history. A message that was missed on its channel is still here, with
 * what happened to it: waiting, sent (and on which channel), on hold, or failed.
 */
@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotificationsComponent {
  private readonly notificationService = inject(NotificationService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly items = signal<NotificationItem[]>([]);
  protected readonly filter = signal<Filter>('ALL');
  protected readonly busyId = signal<number | null>(null);

  protected readonly unreadCount = computed(() => this.items().filter((item) => !item.read).length);
  protected readonly visible = computed(() =>
    this.filter() === 'UNREAD' ? this.items().filter((item) => !item.read) : this.items()
  );

  constructor() {
    this.load();
    // New messages arrive while the page is open: look again from time to time, quietly.
    timer(INBOX_REFRESH_MS, INBOX_REFRESH_MS).pipe(takeUntilDestroyed()).subscribe(() => this.load(true));
  }

  protected setFilter(filter: Filter): void {
    this.filter.set(filter);
  }

  protected kindLabel(kind: NotificationKind): string {
    return KIND_LABELS[kind];
  }

  /** What happened to the message, in words. Says which channel it used, and whether that was the customer's choice or the default. */
  protected delivery(item: NotificationItem): string {
    const channel = item.channel ? CHANNEL_LABELS[item.channel] : null;
    const viaDefault = item.channelSource === 'DEFAULT' ? ' (your default: you have not chosen a channel)' : '';
    switch (item.status) {
      case 'SENT':
        return item.statusReason === 'STUB_DELIVERY'
          ? `Recorded for ${channel} delivery. No ${channel} provider is connected in this environment.${viaDefault}`
          : `Sent by ${channel}.${viaDefault}`;
      case 'QUEUED':
        return item.statusReason ? 'Waiting to be sent. A first attempt did not go through, so we are trying again.' : 'Waiting to be sent.';
      case 'HELD':
        return 'On hold: your profile has no contact detail for the channel you chose. Change your channel in Settings.';
      case 'FAILED':
        return `We could not send this by ${channel ?? 'your channel'}.`;
    }
  }

  protected load(quiet = false): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    if (!quiet) {
      this.loading.set(true);
    }
    this.error.set(null);

    this.notificationService.list(accountId).subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        // A failed quiet refresh must not replace messages the customer is reading.
        if (!quiet) {
          this.error.set(this.errorMessages.getMessage(error));
        }
        this.loading.set(false);
      }
    });
  }

  protected markRead(item: NotificationItem): void {
    if (item.read || this.busyId() !== null) {
      return;
    }
    this.busyId.set(item.id);
    this.actionError.set(null);

    this.notificationService.markRead(this.accountContext.getCurrentAccount().accountNumericId, item.id).subscribe({
      next: (updated) => {
        this.items.update((items) => items.map((entry) => (entry.id === updated.id ? updated : entry)));
        this.busyId.set(null);
      },
      error: (error: unknown) => {
        this.actionError.set(this.errorMessages.getMessage(error));
        this.busyId.set(null);
      }
    });
  }
}
