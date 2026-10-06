import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PnlResponse, PortfolioSummary, PricedPosition } from '../../../core/models/portfolio.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { PortfolioService } from '../../../core/services/portfolio.service';
import { PortfolioComponent, isPricingUnavailable } from './portfolio.component';

describe('PortfolioComponent', () => {
  let fixture: ComponentFixture<PortfolioComponent>;
  const getSummary = vi.fn();
  const getPositions = vi.fn();
  const getPnl = vi.fn();
  const accountPositions = vi.fn();
  const accountBalance = vi.fn();

  const summary = (overrides: Partial<PortfolioSummary> = {}): PortfolioSummary => ({
    accountId: 1,
    baseCurrency: 'USD',
    cashBalance: 24500.75,
    marketValue: 65120,
    costBasis: 61300,
    unrealisedPnl: 3820,
    unrealisedPnlPercent: 6.23,
    realisedPnl: 1145.5,
    totalValue: 89620.75,
    positionCount: 2,
    partial: false,
    asOf: '2026-10-19T14:05:11Z',
    ...overrides
  });

  const position = (overrides: Partial<PricedPosition> = {}): PricedPosition => ({
    accountId: 1,
    symbol: 'AAPL',
    quantity: 200,
    averageCost: 228.4,
    costBasis: 45680,
    lastPrice: 232.71,
    marketValue: 46542,
    unrealisedPnl: 862,
    unrealisedPnlPercent: 1.89,
    currency: 'USD',
    priceAsOf: '2026-10-19T14:04:58Z',
    stale: false,
    ...overrides
  });

  const pnl = (overrides: Partial<PnlResponse> = {}): PnlResponse => ({
    accountId: 1,
    baseCurrency: 'USD',
    from: null,
    to: null,
    realisedPnl: 1145.5,
    unrealisedPnl: 3820,
    totalPnl: 4965.5,
    asOf: '2026-10-19T14:05:11Z',
    ...overrides
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const el = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  const unavailable = () => new HttpErrorResponse({ status: 503, error: { errorCode: 'MKT-503', message: 'Pricing unavailable' } });

  async function build(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PortfolioComponent],
      providers: [
        { provide: PortfolioService, useValue: { getSummary, getPositions, getPnl } },
        { provide: AccountService, useValue: { getPositions: accountPositions, getBalance: accountBalance } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(PortfolioComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    getSummary.mockReset().mockReturnValue(of(summary()));
    getPositions.mockReset().mockReturnValue(of([position()]));
    getPnl.mockReset().mockReturnValue(of(pnl()));
    accountPositions.mockReset().mockReturnValue(of([
      { accountId: 1, symbol: 'AAPL', quantity: 200, averageCost: 228.4 },
      { accountId: 1, symbol: 'SOLD', quantity: 0, averageCost: 10 }
    ]));
    accountBalance.mockReset().mockReturnValue(of({ accountId: 1, cashBalance: 24500.75, currency: 'USD', asOf: '' }));
  });

  it('shows the totals, and says when the portfolio was valued and in what currency', async () => {
    await build();

    expect(el('tile-total')?.textContent).toContain('89,620.75');
    expect(el('tile-total')?.textContent).toContain('USD');
    expect(el('tile-unrealised')?.textContent).toContain('3,820.00');
    expect(el('tile-unrealised')?.textContent).toContain('6.23%');
    expect(el('tile-unrealised')?.textContent).toContain('gain');
    expect(el('tile-realised')?.textContent).toContain('1,145.50');
    expect(el('portfolio-asof')?.textContent).toContain('Valued');
    expect(el('portfolio-asof')?.textContent).toContain('USD');
  });

  it('shows each holding with a live price marker and when the price was observed', async () => {
    await build();

    const row = el('position-AAPL');
    expect(row?.textContent).toContain('232.71');
    expect(row?.textContent).toContain('46,542.00');
    expect(el('price-live')).not.toBeNull();
    expect(el('stale-banner')).toBeNull();
    expect(el('partial-banner')).toBeNull();
  });

  it('marks a stale price visibly and says it is delayed: a stale portfolio is not passed off as live', async () => {
    getPositions.mockReturnValue(of([position({ stale: true })]));
    await build();

    expect(el('price-stale')?.textContent).toContain('Delayed');
    expect(el('price-live')).toBeNull();
    expect(el('stale-banner')?.textContent).toContain('delayed');
    expect(el('position-AAPL')?.textContent).toContain('232.71');   // the number is still shown
  });

  it('shows a partial portfolio: unpriced holdings are listed, marked and called out', async () => {
    getSummary.mockReturnValue(of(summary({ partial: true })));
    getPositions.mockReturnValue(of([
      position(),
      position({ symbol: 'INFY.NS', currency: 'INR', lastPrice: null, marketValue: null, unrealisedPnl: null,
        unrealisedPnlPercent: null, priceAsOf: null, stale: true })
    ]));
    await build();

    expect(el('partial-banner')?.textContent).toContain('1 of your holdings have no price');
    expect(el('position-INFY.NS')?.textContent).toContain('n/a');
    expect(el('price-unavailable')).not.toBeNull();
  });

  it('degrades to the holdings at cost when no price can be had, instead of an empty page', async () => {
    getSummary.mockReturnValue(throwError(unavailable));
    await build();

    expect(el('pricing-unavailable')?.textContent).toContain('Prices are not available right now');
    expect(el('pricing-unavailable')?.textContent).toContain('orders are not affected');
    const table = el('fallback-table');
    expect(table?.textContent).toContain('AAPL');
    expect(table?.textContent).toContain('45,680.00');   // 200 x 228.40 at cost
    expect(table?.textContent).not.toContain('SOLD');    // nothing held
    expect(el('positions-table')).toBeNull();
    expect(accountPositions).toHaveBeenCalledWith(1);
  });

  it('treats only the documented 503 as a pricing outage', () => {
    expect(isPricingUnavailable(unavailable())).toBe(true);
    expect(isPricingUnavailable(new HttpErrorResponse({ status: 503, error: { errorCode: 'REG-503' } }))).toBe(false);
    expect(isPricingUnavailable(new HttpErrorResponse({ status: 500 }))).toBe(false);
    expect(isPricingUnavailable(new Error('x'))).toBe(false);
  });

  it('shows any other failure as an error rather than as an outage', async () => {
    getSummary.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 403, error: { errorCode: 'ACC-403', message: 'no' } })));
    await build();

    expect(el('portfolio-error')?.textContent).toContain('not the one this sign-in may trade');
    expect(el('pricing-unavailable')).toBeNull();
  });

  it('asks for the profit and loss of a date range and keeps the holdings if the range is refused', async () => {
    await build();
    getPnl.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { errorCode: 'VAL-422' } })));

    const from = el('pnl-from') as HTMLInputElement;
    from.value = '2026-10-31';
    from.dispatchEvent(new Event('input'));
    const to = el('pnl-to') as HTMLInputElement;
    to.value = '2026-10-01';
    to.dispatchEvent(new Event('input'));
    (el('pnl-apply') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(getPnl).toHaveBeenLastCalledWith(1, '2026-10-31', '2026-10-01', false);
    expect(el('pnl-error')?.textContent).toContain('date range is not valid');
    expect(el('positions-table')).not.toBeNull();
    expect(el('pnl-realised')?.textContent).toContain('1,145.50');   // the earlier figures stay on screen
  });

  it('shows the breakdown by instrument when asked', async () => {
    await build();
    getPnl.mockReturnValue(of(pnl({ bySymbol: [
      { symbol: 'AAPL', realisedPnl: 845.5, unrealisedPnl: 862, totalPnl: 1707.5 },
      { symbol: 'MSFT', realisedPnl: 300, unrealisedPnl: 0, totalPnl: 300 }
    ] })));

    const box = el('pnl-by-symbol') as HTMLInputElement;
    box.checked = true;
    box.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(getPnl).toHaveBeenLastCalledWith(1, undefined, undefined, true);
    expect(el('pnl-symbol-table')?.textContent).toContain('MSFT');
    expect(el('pnl-symbol-table')?.textContent).toContain('1,707.50');
  });

  it('welcomes an account with nothing held', async () => {
    getPositions.mockReturnValue(of([]));
    getSummary.mockReturnValue(of(summary({ positionCount: 0, marketValue: 0, costBasis: 0, unrealisedPnl: 0,
      unrealisedPnlPercent: null, totalValue: 24500.75 })));
    await build();

    expect(el('no-holdings')).not.toBeNull();
  });
});
