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
  const update = vi.fn();
  const setEnabled = vi.fn();
  const remove = vi.fn();

  const alert = (overrides: Partial<PriceAlert> = {}): PriceAlert => ({
    id: 7,
    symbol: 'AAPL',
    direction: 'GTE',
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
        { provide: AlertService, useValue: { list, create, update, setEnabled, remove } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
        {
          provide: HomeCurrencyService,
          useValue: {
            ensureLoaded: vi.fn(),
            homeCurrency: signal('USD'),
            currencyOf: (symbol: string) => (symbol === 'MSFT' ? 'USD' : 'USD'),
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

  function openDialogAndFill(symbol: string, threshold: string): void {
    (el('open-create-alert') as HTMLButtonElement).click();
    fixture.detectChanges();
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
    update.mockReset();
    setEnabled.mockReset();
    remove.mockReset();
  });

  it('lists alerts and shows the total count', async () => {
    await build();
    expect(el('alert-7')?.textContent).toContain('AAPL');
    expect(el('alert-state')?.textContent).toContain('Enabled');
    expect(el('alert-count')?.textContent).toContain('1');
  });

  it('creates an alert from the dialog', async () => {
    await build();
    create.mockReturnValue(of(alert({ id: 8, symbol: 'MSFT', threshold: 300 })));

    openDialogAndFill('MSFT', '300');
    (el('alert-create') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(create).toHaveBeenCalledWith(1, { symbol: 'MSFT', direction: 'GT', threshold: 300 });
    expect(el('alert-8')).not.toBeNull();
  });

  it('edits an alert', async () => {
    await build();
    update.mockReturnValue(of(alert({ id: 7, direction: 'LTE', threshold: 200 })));
    (el('alert-edit') as HTMLButtonElement).click();
    fixture.detectChanges();

    const direction = el('alert-direction') as HTMLSelectElement;
    direction.value = 'LTE';
    direction.dispatchEvent(new Event('change'));
    const input = el('alert-threshold') as HTMLInputElement;
    input.value = '200';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (el('alert-create') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(update).toHaveBeenCalledWith(1, 7, { symbol: 'AAPL', direction: 'LTE', threshold: 200 });
  });

  it('toggles enabled state with the switch', async () => {
    await build();
    setEnabled.mockReturnValue(of(alert({ status: 'CANCELLED' })));
    const switchEl = el('alert-enabled-switch') as HTMLInputElement;
    switchEl.checked = false;
    switchEl.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(setEnabled).toHaveBeenCalledWith(1, 7, false);
    expect(el('alert-state')?.textContent).toContain('Disabled');
  });

  it('deletes an active alert by cancel then remove flow', async () => {
    await build();
    remove.mockReset()
      .mockReturnValueOnce(of(alert({ status: 'CANCELLED' })))
      .mockReturnValueOnce(of(alert({ status: 'CANCELLED' })));
    list.mockReturnValue(of([]));

    (el('alert-delete') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(remove).toHaveBeenCalledTimes(2);
    expect(remove).toHaveBeenNthCalledWith(1, 1, 7);
    expect(remove).toHaveBeenNthCalledWith(2, 1, 7);
  });

  it('filters to disabled alerts', async () => {
    list.mockReturnValue(of([alert({ id: 1 }), alert({ id: 2, status: 'CANCELLED' })]));
    await build();

    (el('filter-disabled-alerts') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el('alert-2')).not.toBeNull();
    expect(el('alert-1')).toBeNull();
  });

  it('shows trigger history per alert', async () => {
    list.mockReturnValue(of([alert({
      status: 'TRIGGERED',
      deliveryState: 'HANDED_OVER',
      triggeredPrice: 235.4,
      triggeredAt: '2026-10-06T09:15:00Z'
    })]));
    await build();
    (el('alert-history-toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el('alert-history-7')?.textContent).toContain('Notification recorded in Inbox');
  });

  it('shows a currency badge in the side form', async () => {
    await build();
    (el('open-create-alert') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el('alert-currency-badge')?.textContent).toContain('USD');
  });

  it('shows server reason when level already crossed', async () => {
    await build();
    create.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 422,
      error: { errorCode: 'VAL-422', message: 'The price of AAPL is already above that level. Choose a level the price has not reached yet' }
    })));

    openDialogAndFill('AAPL', '100');
    (el('alert-create') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el('alert-create-error')?.textContent).toContain('already above that level');
  });
});
