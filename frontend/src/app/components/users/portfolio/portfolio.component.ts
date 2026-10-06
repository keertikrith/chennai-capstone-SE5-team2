import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BalanceResponse, PositionResponse } from '../../../core/models/account.model';
import { PnlResponse, PortfolioSummary, PricedPosition } from '../../../core/models/portfolio.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { PortfolioService } from '../../../core/services/portfolio.service';

/** True for the documented "no price for any holding" answer: 503 with MKT-503. */
export function isPricingUnavailable(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 503 && error.error?.errorCode === 'MKT-503';
}

/**
 * Holdings priced against live quotes, with profit and loss.
 *
 * Three honest states, none of which hides what the platform knows:
 * <ul>
 *   <li>Priced: every figure carries when its price was observed.</li>
 *   <li>Stale or partial: the number is shown and marked (a price that is old, or a holding that could
 *       not be priced and is left out of the totals). A stale portfolio is never passed off as live.</li>
 *   <li>Pricing unavailable (503 MKT-503): the page falls back to the holdings at cost, from the
 *       account routes, instead of an empty screen. Trading is unaffected.</li>
 * </ul>
 * Prices reach this page through the platform only; the browser never calls a quote provider.
 */
@Component({
  selector: 'app-portfolio',
  standalone: true,
  imports: [DecimalPipe, DatePipe, FormsModule],
  templateUrl: './portfolio.component.html',
  styleUrl: './portfolio.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PortfolioComponent {
  private readonly portfolioService = inject(PortfolioService);
  private readonly accountService = inject(AccountService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pricingDown = signal(false);
  protected readonly pnlError = signal<string | null>(null);

  protected readonly summary = signal<PortfolioSummary | null>(null);
  protected readonly positions = signal<PricedPosition[]>([]);
  protected readonly pnl = signal<PnlResponse | null>(null);

  /** What the page shows when no price can be had: the holdings and cash, unpriced. */
  protected readonly unpriced = signal<PositionResponse[]>([]);
  protected readonly cash = signal<BalanceResponse | null>(null);

  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly bySymbol = signal(false);

  /** Positions that have a price but an old one. */
  protected readonly staleCount = computed(
    () => this.positions().filter((position) => position.lastPrice !== null && position.stale).length
  );
  /** Positions that could not be priced at all and are left out of the totals. */
  protected readonly unpricedCount = computed(
    () => this.positions().filter((position) => position.lastPrice === null).length
  );
  protected readonly fallbackRows = computed(() =>
    this.unpriced().map((position) => ({
      ...position,
      costBasis: position.quantity * position.averageCost
    }))
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    this.loading.set(true);
    this.error.set(null);
    this.pricingDown.set(false);

    this.portfolioService.getSummary(accountId).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loadDetails(accountId);
      },
      error: (error: unknown) => {
        if (isPricingUnavailable(error)) {
          this.pricingDown.set(true);
          this.summary.set(null);
          this.positions.set([]);
          this.pnl.set(null);
          this.loadUnpriced(accountId);
        } else {
          this.error.set(this.errorMessages.getMessage(error));
          this.loading.set(false);
        }
      }
    });
  }

  protected applyRange(): void {
    this.loadPnl(this.accountContext.getCurrentAccount().accountNumericId);
  }

  protected toggleBySymbol(checked: boolean): void {
    this.bySymbol.set(checked);
    this.applyRange();
  }

  /** The sign of a profit or loss, as a word as well as a colour. */
  protected direction(value: number | null): 'gain' | 'loss' | 'flat' {
    if (value === null || value === 0) {
      return 'flat';
    }
    return value > 0 ? 'gain' : 'loss';
  }

  private loadDetails(accountId: number): void {
    forkJoin({
      positions: this.portfolioService.getPositions(accountId),
      pnl: this.pnlRequest(accountId)
    }).subscribe({
      next: ({ positions, pnl }) => {
        this.positions.set(positions);
        this.pnl.set(pnl);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        // The summary worked a moment ago; if the prices have gone since, say so instead of showing half a page.
        if (isPricingUnavailable(error)) {
          this.pricingDown.set(true);
          this.summary.set(null);
          this.loadUnpriced(accountId);
        } else {
          this.error.set(this.errorMessages.getMessage(error));
          this.loading.set(false);
        }
      }
    });
  }

  private loadPnl(accountId: number): void {
    this.pnlError.set(null);
    this.pnlRequest(accountId).subscribe((pnl) => {
      // A failed request keeps the figures already on screen next to the error message.
      if (pnl) {
        this.pnl.set(pnl);
      }
    });
  }

  private pnlRequest(accountId: number) {
    this.pnlError.set(null);
    return this.portfolioService
      .getPnl(accountId, this.from() || undefined, this.to() || undefined, this.bySymbol())
      .pipe(
        catchError((error: unknown) => {
          // A bad date range or a P&L hiccup must not blank the holdings next to it.
          this.pnlError.set(
            error instanceof HttpErrorResponse && error.status === 422
              ? 'That date range is not valid. The start date must not be after the end date.'
              : this.errorMessages.getMessage(error)
          );
          return of(null);
        })
      );
  }

  private loadUnpriced(accountId: number): void {
    forkJoin({
      balance: this.accountService.getBalance(accountId).pipe(catchError(() => of(null))),
      positions: this.accountService.getPositions(accountId).pipe(catchError(() => of([] as PositionResponse[])))
    }).subscribe(({ balance, positions }) => {
      this.cash.set(balance);
      this.unpriced.set(positions.filter((position) => position.quantity > 0));
      this.loading.set(false);
    });
  }
}
