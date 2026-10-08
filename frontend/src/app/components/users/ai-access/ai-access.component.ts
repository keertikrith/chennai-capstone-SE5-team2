import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AiAuditEntry, AiConnector, AiScope } from '../../../core/models/ai-access.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AiAccessService } from '../../../core/services/ai-access.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { ToastService } from '../../../core/ui/toast.service';
import { IconComponent } from '../shared/icon/icon.component';

/**
 * AI assistant access. The customer makes a private link, pastes it into Claude or ChatGPT, and the assistant can
 * then read the account, set alerts and (only if allowed here) place orders the customer confirms in the chat.
 * The link is the secret: it is shown once, here, and the server keeps only a hash.
 */
@Component({
  selector: 'app-ai-access',
  standalone: true,
  imports: [DatePipe, IconComponent, RouterLink],
  templateUrl: './ai-access.component.html',
  styleUrl: './ai-access.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AiAccessComponent {
  private readonly service = inject(AiAccessService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly toast = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly connectors = signal<AiConnector[]>([]);
  protected readonly activity = signal<AiAuditEntry[]>([]);

  protected readonly formOpen = signal(false);
  protected readonly creating = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly label = signal('');
  protected readonly allowAlerts = signal(true);
  protected readonly allowTrade = signal(false);
  protected readonly maxOrderValue = signal(5000);
  protected readonly maxOrdersPerDay = signal(3);
  protected readonly expiresInDays = signal(30);

  /** The link just made. It is held in memory only, until the customer dismisses it. */
  protected readonly newLink = signal<string | null>(null);
  protected readonly copied = signal(false);
  protected readonly revoking = signal<string | null>(null);

  protected readonly active = computed(() => this.connectors().filter((c) => c.status === 'ACTIVE'));
  protected readonly inactive = computed(() => this.connectors().filter((c) => c.status !== 'ACTIVE'));

  constructor() {
    this.load();
  }

  private accountId(): number {
    return this.accountContext.getTokenAccountId();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.service.list(this.accountId()).subscribe({
      next: (connectors) => {
        this.connectors.set(connectors);
        this.loading.set(false);
        this.loadActivity();
      },
      error: (error: unknown) => {
        this.loadError.set(this.errorMessages.getMessage(error));
        this.loading.set(false);
      }
    });
  }

  private loadActivity(): void {
    this.service.activity(this.accountId()).subscribe({
      next: (entries) => this.activity.set(entries),
      error: () => this.activity.set([])
    });
  }

  protected openForm(): void {
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
  }

  protected scopesOf(): AiScope[] {
    const scopes: AiScope[] = ['READ'];
    if (this.allowAlerts()) scopes.push('ALERTS');
    if (this.allowTrade()) scopes.push('TRADE');
    return scopes;
  }

  protected create(): void {
    const label = this.label().trim();
    if (!label) {
      this.formError.set('Give the link a name, such as "My Claude".');
      return;
    }
    this.creating.set(true);
    this.formError.set(null);
    this.service
      .create(this.accountId(), {
        label,
        scopes: this.scopesOf(),
        maxOrderValue: this.allowTrade() ? this.maxOrderValue() : undefined,
        maxOrdersPerDay: this.allowTrade() ? this.maxOrdersPerDay() : undefined,
        expiresInDays: this.expiresInDays()
      })
      .subscribe({
        next: (created) => {
          this.creating.set(false);
          this.formOpen.set(false);
          this.label.set('');
          this.copied.set(false);
          this.newLink.set(created.url);
          this.connectors.update((list) => [created.connector, ...list]);
          this.loadActivity();
        },
        error: (error: unknown) => {
          this.creating.set(false);
          this.formError.set(this.errorMessages.getMessage(error));
        }
      });
  }

  protected async copyLink(): Promise<void> {
    const link = this.newLink();
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link);
      this.copied.set(true);
      this.toast.show('Link copied');
    } catch {
      this.toast.error('Could not copy. Select the link and copy it yourself.');
    }
  }

  protected dismissLink(): void {
    this.newLink.set(null);
    this.copied.set(false);
  }

  protected revoke(connector: AiConnector): void {
    this.revoking.set(connector.id);
    this.service.revoke(this.accountId(), connector.id).subscribe({
      next: () => {
        this.revoking.set(null);
        this.connectors.update((list) =>
          list.map((c) => (c.id === connector.id ? { ...c, status: 'REVOKED' as const } : c))
        );
        this.toast.show(`"${connector.label}" can no longer reach your account`);
        this.loadActivity();
      },
      error: (error: unknown) => {
        this.revoking.set(null);
        this.toast.error(this.errorMessages.getMessage(error));
      }
    });
  }

  protected canTrade(connector: AiConnector): boolean {
    return connector.scopes.includes('TRADE');
  }

  protected canAlert(connector: AiConnector): boolean {
    return connector.scopes.includes('ALERTS');
  }

  protected numberValue(event: Event, fallback: number): number {
    const value = Number((event.target as HTMLInputElement).value);
    return Number.isFinite(value) && value > 0 ? value : fallback;
  }
}
