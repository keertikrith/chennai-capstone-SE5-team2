import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, ValidationErrors, Validators, AbstractControl } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { timer } from 'rxjs';
import { AlertDirection, MAX_ALERTS_PER_ACCOUNT, PriceAlert } from '../../../core/models/alert.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AlertService } from '../../../core/services/alert.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';

/** How often the list looks again, so a fired alert shows up without a manual refresh. */
export const ALERT_REFRESH_MS = 15_000;

function positiveAmount(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return /^\d+(\.\d{1,4})?$/.test(value) && Number(value) > 0 ? null : { amount: true };
}

/**
 * Price alerts: tell me when this crosses that level. An alert fires once, is handed to notifications,
 * and reaches the customer on the channel they chose in Settings. This page never sends anything: it
 * only creates alerts and shows what became of them, including whether a fired alert was handed over.
 */
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

  /** Anything the platform lists as tradable can have an alert; the server refuses a symbol it does not know. */
  protected readonly instruments = computed(() => this.home.instrumentList().filter((item) => item.tradable));
  protected readonly remaining = computed(() => Math.max(0, this.cap - this.alerts().length));

  protected readonly form = this.formBuilder.nonNullable.group({
    symbol: ['', [Validators.required]],
    direction: ['ABOVE' as AlertDirection, [Validators.required]],
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
    this.createError.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.creating()) {
      return;
    }
    const value = this.form.getRawValue();

    this.creating.set(true);
    this.alertService
      .create(this.accountContext.getCurrentAccount().accountNumericId, {
        symbol: value.symbol.trim().toUpperCase(),
        direction: value.direction,
        threshold: Number(value.threshold)
      })
      .subscribe({
        next: (created) => {
          this.alerts.update((alerts) => [created, ...alerts]);
          this.form.controls.threshold.reset('');
          this.creating.set(false);
        },
        error: (error: unknown) => {
          this.creating.set(false);
          this.createError.set(this.createMessage(error));
        }
      });
  }

  /** Cancels an active alert, or removes a finished one. */
  protected remove(alert: PriceAlert): void {
    if (this.busyId() !== null) {
      return;
    }
    this.busyId.set(alert.id);
    this.actionError.set(null);

    this.alertService.remove(this.accountContext.getCurrentAccount().accountNumericId, alert.id).subscribe({
      next: () => {
        this.busyId.set(null);
        // The server decides what happened (cancelled, removed, or fired meanwhile): show the truth.
        this.load(true);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.actionError.set(this.errorMessages.getMessage(error));
        this.load(true);
      }
    });
  }

  protected canRemove(alert: PriceAlert): boolean {
    // A fired alert whose hand-over is unresolved is kept so the customer can see it was not delivered.
    return !(alert.status === 'TRIGGERED' && (alert.deliveryState === 'PENDING_HANDOVER' || alert.deliveryState === 'HANDOVER_FAILED'));
  }

  protected removeLabel(alert: PriceAlert): string {
    return alert.status === 'ACTIVE' ? 'Cancel' : 'Remove';
  }

  protected describe(alert: PriceAlert): string {
    return `${alert.direction === 'ABOVE' ? 'rises to or above' : 'falls to or below'} ${alert.threshold}`;
  }

  /** What became of the alert, in words: has it fired, and if so, were they told. */
  protected statusText(alert: PriceAlert): string {
    switch (alert.status) {
      case 'ACTIVE':
        return 'Watching';
      case 'CANCELLED':
        return 'Cancelled';
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

  private createMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 422 && typeof error.error?.message === 'string'
      && error.error.message !== 'Invalid input') {
      return error.error.message;
    }
    return this.errorMessages.getMessage(error);
  }
}
