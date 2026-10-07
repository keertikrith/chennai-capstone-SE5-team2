import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, ValidationErrors, Validators, AbstractControl } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { of, switchMap, timer } from 'rxjs';
import { AlertDirection, MAX_ALERTS_PER_ACCOUNT, PriceAlert } from '../../../core/models/alert.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AlertService } from '../../../core/services/alert.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';

// How often the list looks again, so a fired alert shows up without a manual refresh.
export const ALERT_REFRESH_MS = 15_000;
type AlertFilter = 'ALL' | 'ENABLED' | 'DISABLED' | 'TRIGGERED';
type AlertHistoryCard = { title: string; detail: string };

type OperatorOption = { value: AlertDirection; symbol: string; label: string };

const OPERATOR_OPTIONS: OperatorOption[] = [
  { value: 'GT', symbol: '>', label: 'greater than' },
  { value: 'GTE', symbol: '>=', label: 'greater than or equal to' },
  { value: 'LT', symbol: '<', label: 'less than' },
  { value: 'LTE', symbol: '<=', label: 'less than or equal to' },
  { value: 'EQ', symbol: '==', label: 'equal to' }
];

function positiveAmount(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return /^\d+(\.\d{1,4})?$/.test(value) && Number(value) > 0 ? null : { amount: true };
}

// Price alerts: tell me when this crosses that level. An alert fires once, is handed to notifications,
// and reaches the customer on the channel they chose in Settings. This page never sends anything: it
// only creates alerts and shows what became of them, including whether a fired alert was handed over.
@Component({
  selector: 'app-alerts',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './alerts.component.html',
  styleUrl: './alerts.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AlertsComponent {
  private readonly alertService = inject(AlertService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  protected readonly home = inject(HomeCurrencyService);

  protected readonly cap = MAX_ALERTS_PER_ACCOUNT;
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly createError = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly creating = signal(false);
  protected readonly busyId = signal<number | null>(null);
  protected readonly alerts = signal<PriceAlert[]>([]);
  protected readonly filter = signal<AlertFilter>('ALL');
  protected readonly showDialog = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly historyId = signal<number | null>(null);
  protected readonly operatorOptions = OPERATOR_OPTIONS;

  /** Anything the platform lists as tradable can have an alert; the server refuses a symbol it does not know. */
  protected readonly instruments = computed(() => this.home.instrumentList().filter((item) => item.tradable));
  protected readonly remaining = computed(() => Math.max(0, this.cap - this.alerts().length));
  protected readonly visibleAlerts = computed(() => this.alerts().filter((alert) => this.matchesFilter(alert)));

  protected readonly form = this.formBuilder.nonNullable.group({
    symbol: ['', [Validators.required]],
    direction: ['GT' as AlertDirection, [Validators.required]],
    threshold: ['', [Validators.required, positiveAmount]]
  });

  constructor() {
    this.home.ensureLoaded();
    // The watchlist's "Set a price alert" link arrives with the symbol already chosen.
    const symbol = this.route.snapshot.queryParamMap.get('symbol')?.trim().toUpperCase();
    if (symbol && /^[A-Z0-9.:^=-]{1,20}$/.test(symbol)) {
      this.form.controls.symbol.setValue(symbol);
    }
    this.load();
    timer(ALERT_REFRESH_MS, ALERT_REFRESH_MS).pipe(takeUntilDestroyed()).subscribe(() => this.load(true));
  }

  protected setFilter(filter: AlertFilter): void {
    this.filter.set(filter);
  }

  protected load(quiet = false): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    if (!quiet) {
      this.loading.set(true);
    }
    this.error.set(null);

    this.alertService.list(accountId).subscribe({
      next: (alerts) => {
        this.alerts.set(alerts);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        if (!quiet) {
          this.error.set(this.errorMessages.getMessage(error));
        }
        this.loading.set(false);
      }
    });
  }

  protected create(): void {
    this.save();
  }

  protected openCreate(): void {
    this.createError.set(null);
    this.editingId.set(null);
    this.form.controls.direction.setValue('GT');
    this.form.controls.threshold.reset('');
    this.showDialog.set(true);
  }

  protected openEdit(alert: PriceAlert): void {
    if (!this.canEdit(alert)) {
      return;
    }
    this.createError.set(null);
    this.editingId.set(alert.id);
    this.form.setValue({
      symbol: alert.symbol,
      direction: this.normalisedDirection(alert.direction),
      threshold: String(alert.threshold)
    });
    this.showDialog.set(true);
  }

  protected closeDialog(): void {
    if (this.creating()) {
      return;
    }
    this.showDialog.set(false);
    this.editingId.set(null);
    this.form.markAsPristine();
  }

  protected save(): void {
    this.createError.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.creating()) {
      return;
    }
    const value = this.form.getRawValue();

    this.creating.set(true);
    const request = {
      symbol: value.symbol.trim().toUpperCase(),
      direction: value.direction,
      threshold: Number(value.threshold)
    };
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    const editingId = this.editingId();
    const submit = editingId === null
      ? this.alertService.create(accountId, request)
      : this.alertService.update(accountId, editingId, request);

    submit.subscribe({
      next: (saved) => {
        this.alerts.update((alerts) =>
          editingId === null ? [saved, ...alerts] : alerts.map((entry) => (entry.id === saved.id ? saved : entry))
        );
        this.form.controls.threshold.reset('');
        this.creating.set(false);
        this.showDialog.set(false);
        this.editingId.set(null);
      },
      error: (error: unknown) => {
        this.creating.set(false);
        this.createError.set(this.createMessage(error));
      }
    });
  }

  /** Cancels an active alert, or removes a finished one. */
  protected delete(alert: PriceAlert): void {
    if (this.busyId() !== null) {
      return;
    }
    this.busyId.set(alert.id);
    this.actionError.set(null);
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;

    this.alertService.remove(accountId, alert.id).pipe(
      switchMap((result) => result.status === 'CANCELLED' ? this.alertService.remove(accountId, alert.id) : of(result))
    ).subscribe({
      next: () => {
        this.busyId.set(null);
        this.historyId.update((id) => (id === alert.id ? null : id));
        this.load(true);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.actionError.set(this.errorMessages.getMessage(error));
        this.load(true);
      }
    });
  }

  protected setEnabled(alert: PriceAlert, event: Event): void {
    if (this.busyId() !== null || alert.status === 'TRIGGERED') {
      return;
    }
    const input = event.target as HTMLInputElement;
    this.busyId.set(alert.id);
    this.actionError.set(null);
    this.alertService.setEnabled(this.accountContext.getCurrentAccount().accountNumericId, alert.id, input.checked).subscribe({
      next: (updated) => {
        this.alerts.update((alerts) => alerts.map((entry) => (entry.id === updated.id ? updated : entry)));
        this.busyId.set(null);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.actionError.set(this.errorMessages.getMessage(error));
        this.load(true);
      }
    });
  }

  protected canDelete(alert: PriceAlert): boolean {
    return !(alert.status === 'TRIGGERED' && (alert.deliveryState === 'PENDING_HANDOVER' || alert.deliveryState === 'HANDOVER_FAILED'));
  }

  protected canEdit(alert: PriceAlert): boolean {
    return alert.status !== 'TRIGGERED';
  }

  protected isEnabled(alert: PriceAlert): boolean {
    return alert.status === 'ACTIVE';
  }

  protected statusLabel(alert: PriceAlert): string {
    if (alert.status === 'ACTIVE') {
      return 'Enabled';
    }
    if (alert.status === 'CANCELLED') {
      return 'Disabled';
    }
    return 'Triggered';
  }

  protected toggleHistory(alert: PriceAlert): void {
    this.historyId.update((id) => (id === alert.id ? null : alert.id));
  }

  protected describe(alert: PriceAlert): string {
    const direction = this.normalisedDirection(alert.direction);
    const symbol = this.operatorSymbol(direction);
    return `${symbol} ${alert.threshold}`;
  }

  /** What became of the alert, in words: has it fired, and if so, were they told. */
  protected statusText(alert: PriceAlert): string {
    switch (alert.status) {
      case 'ACTIVE':
        return 'Enabled';
      case 'CANCELLED':
        return 'Disabled';
      case 'TRIGGERED':
        switch (alert.deliveryState) {
          case 'HANDED_OVER':
            return 'Fired. We have sent you the message: see your Inbox.';
          case 'HANDOVER_FAILED':
            return 'Fired, but the message could not be queued yet. We are trying again.';
          default:
            return 'Fired. The message is being queued.';
        }
    }
  }

  protected history(alert: PriceAlert): string[] {
    const entries = [`Created at ${new Date(alert.createdAt).toLocaleString()}`];
    if (alert.status === 'CANCELLED') {
      entries.push('Disabled by user');
    }
    if (alert.status === 'TRIGGERED') {
      entries.push(`Triggered at ${new Date(alert.triggeredAt ?? alert.createdAt).toLocaleString()}`);
      if (alert.deliveryState === 'HANDED_OVER') {
        entries.push('Notification recorded in Inbox');
      } else if (alert.deliveryState === 'HANDOVER_FAILED') {
        entries.push('Notification handover failed; retrying');
      } else {
        entries.push('Notification handover pending');
      }
    }
    return entries;
  }

  protected historyCards(alert: PriceAlert): AlertHistoryCard[] {
    const cards: AlertHistoryCard[] = [];
    if (alert.status === 'TRIGGERED') {
      const triggeredAt = alert.triggeredAt ? new Date(alert.triggeredAt).toLocaleString() : 'n/a';
      const triggeredPrice = alert.triggeredPrice === null ? 'n/a' : `${alert.triggeredPrice}`;
      cards.push({
        title: 'Alert triggered',
        detail: `Triggered at ${triggeredAt} when market price was ${triggeredPrice} (${this.currencyForAlert(alert)}).`
      });
      if (alert.deliveryState === 'HANDED_OVER') {
        cards.push({ title: 'Notification status', detail: 'Notification recorded in Inbox.' });
      } else if (alert.deliveryState === 'HANDOVER_FAILED') {
        cards.push({ title: 'Notification status', detail: 'Notification handover failed; retrying.' });
      } else {
        cards.push({ title: 'Notification status', detail: 'Notification handover pending.' });
      }
    } else {
      cards.push({ title: 'Not triggered yet', detail: 'This alert has not triggered yet.' });
    }
    return cards;
  }

  protected isEditing(alert: PriceAlert): boolean {
    return this.editingId() === alert.id;
  }

  protected operatorSymbol(direction: AlertDirection): string {
    const normalised = this.normalisedDirection(direction);
    const option = this.operatorOptions.find((entry) => entry.value === normalised);
    return option?.symbol ?? '>=';
  }

  protected thresholdCurrency(): string {
    const symbol = this.form.controls.symbol.value.trim().toUpperCase();
    return this.home.currencyOf(symbol) ?? this.home.homeCurrency() ?? 'USD';
  }

  protected currencyForAlert(alert: PriceAlert): string {
    return this.home.currencyOf(alert.symbol) ?? this.home.homeCurrency() ?? 'USD';
  }

  private normalisedDirection(direction: AlertDirection): AlertDirection {
    if (direction === 'ABOVE') {
      return 'GTE';
    }
    if (direction === 'BELOW') {
      return 'LTE';
    }
    return direction;
  }

  private matchesFilter(alert: PriceAlert): boolean {
    switch (this.filter()) {
      case 'ENABLED':
        return alert.status === 'ACTIVE';
      case 'DISABLED':
        return alert.status === 'CANCELLED';
      case 'TRIGGERED':
        return alert.status === 'TRIGGERED';
      default:
        return true;
    }
  }

  private createMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 422 && typeof error.error?.message === 'string'
      && error.error.message !== 'Invalid input') {
      return error.error.message;
    }
    return this.errorMessages.getMessage(error);
  }
}
