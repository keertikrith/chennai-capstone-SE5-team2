import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PriceAlert } from '../../../core/models/alert.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AlertService } from '../../../core/services/alert.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { AlertsComponent } from './alerts.component';

describe('AlertsComponent', () => {
  let fixture: ComponentFixture<AlertsComponent>;
  const list = vi.fn();
  const create = vi.fn();
  const remove = vi.fn();

  const alert = (overrides: Partial<PriceAlert> = {}): PriceAlert => ({
    id: 7,
    symbol: 'AAPL',
    direction: 'ABOVE',
    threshold: 235,
    status: 'ACTIVE',
    deliveryState: 'NONE',
    triggeredPrice: null,
    triggeredAt: null,
    createdAt: '2026-10-06T09:00:00Z',
    ...overrides
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const el = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  async function build(query: Record<string, string> = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AlertsComponent],
      providers: [
        provideRouter([]),
        { provide: AlertService, useValue: { list, create, remove } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
        {
          provide: HomeCurrencyService,
          useValue: {
            ensureLoaded: vi.fn(),
            instrumentList: signal([
              { symbol: 'AAPL', name: 'Apple', exchange: 'NASDAQ', assetClass: 'US_EQUITY', currency: 'USD', tradable: true },
              { symbol: 'MSFT', name: 'Microsoft', exchange: 'NASDAQ', assetClass: 'US_EQUITY', currency: 'USD', tradable: true }
            ])
          }
        }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AlertsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function fill(symbol: string, threshold: string): void {
    const select = el('alert-symbol') as HTMLSelectElement;
    select.value = symbol;
    select.dispatchEvent(new Event('change'));
    const input = el('alert-threshold') as HTMLInputElement;
    input.value = threshold;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    list.mockReset().mockReturnValue(of([alert()]));
    create.mockReset();
    remove.mockReset();
  });

  it('lists the alerts with their state', async () => {
    await build();

    expect(el('alert-7')?.textContent).toContain('AAPL');
    expect(el('alert-state')?.textContent).toContain('Watching');
  });

  it('tells the customer a fired alert was handed over', async () => {
    list.mockReturnValue(of([alert({ status: 'TRIGGERED', deliveryState: 'HANDED_OVER', triggeredPrice: 235.4,
      triggeredAt: '2026-10-06T09:15:00Z' })]));
    await build();

    expect(el('alert-state')?.textContent).toContain('Fired');
    expect(el('alert-state')?.textContent).toContain('Inbox');
    expect(text()).toContain('235.40');
  });

  it('tells the customer when a fired alert could not be queued, instead of saying nothing', async () => {
    list.mockReturnValue(of([alert({ status: 'TRIGGERED', deliveryState: 'HANDOVER_FAILED', triggeredPrice: 235.4,
      triggeredAt: '2026-10-06T09:15:00Z' })]));
    await build();

    expect(el('alert-state')?.textContent).toContain('could not be queued');
    expect(el('alert-remove')).toBeNull();   // it cannot be removed while the customer may not have been told
  });

  it('creates an alert with the chosen symbol, direction and level', async () => {
    await build();
    create.mockReturnValue(of(alert({ id: 8, symbol: 'MSFT', threshold: 300 })));

    fill('MSFT', '300');
    (el('alert-create') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(create).toHaveBeenCalledWith(1, { symbol: 'MSFT', direction: 'ABOVE', threshold: 300 });
    expect(el('alert-8')).not.toBeNull();
  });

  it('does not ask the server when the level is not a positive number', async () => {
    await build();

    fill('AAPL', '-5');
    (el('alert-create') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(create).not.toHaveBeenCalled();
    expect(text()).toContain('Enter a price above zero');
  });

  it('preselects the symbol that arrives from the watchlist link', async () => {
    await build({ symbol: 'msft' });

    expect((el('alert-symbol') as HTMLSelectElement).value).toBe('MSFT');
  });

  it('ignores a symbol in the link that is not a plausible symbol', async () => {
    await build({ symbol: '<script>alert(1)</script>' });

    expect((el('alert-symbol') as HTMLSelectElement).value).toBe('');
  });

  it('explains the cap in plain words', async () => {
    await build();
    create.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { errorCode: 'ALR-409', message: 'You can keep at most 25 alerts' }
    })));

    fill('AAPL', '240');
    (el('alert-create') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el('alert-create-error')?.textContent).toContain('limit for price alerts');
  });

  it('shows the server\'s reason when the price has already crossed the level', async () => {
    await build();
    create.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 422,
      error: { errorCode: 'VAL-422', message: 'The price of AAPL is already above that level. Choose a level the price has not reached yet' }
    })));

    fill('AAPL', '100');
    (el('alert-create') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el('alert-create-error')?.textContent).toContain('already above that level');
  });

  it('cancels an active alert and re-reads what the server says', async () => {
    await build();
    remove.mockReturnValue(of(alert({ status: 'CANCELLED' })));
    list.mockReturnValue(of([alert({ status: 'CANCELLED' })]));

    (el('alert-remove') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(remove).toHaveBeenCalledWith(1, 7);
    expect(el('alert-state')?.textContent).toContain('Cancelled');
  });
});
