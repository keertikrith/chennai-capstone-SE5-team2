import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PreferencesResponse } from '../../../core/models/preferences.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { PreferencesService } from '../../../core/services/preferences.service';
import { SettingsComponent } from './settings.component';

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  const get = vi.fn();
  const update = vi.fn();

  const prefs = (overrides: Partial<PreferencesResponse> = {}): PreferencesResponse => ({
    accountId: 1,
    defaultAccountId: 1,
    alertChannel: 'SMS',
    contactDetail: '******3210',
    source: 'STORED',
    channels: [
      { channel: 'EMAIL', available: true, hint: 'j***@example.com' },
      { channel: 'SMS', available: true, hint: '******3210' },
      { channel: 'PUSH', available: true, hint: 'In-app inbox' }
    ],
    updatedAt: '2026-10-06T10:00:00Z',
    ...overrides
  });

  const el = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        { provide: PreferencesService, useValue: { get, update } },
        { provide: AccountContextService, useValue: { getTokenAccountId: () => 1, getAllowedAccountIds: () => [1] } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(SettingsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    get.mockReset().mockReturnValue(of(prefs()));
    update.mockReset();
  });

  it('shows the stored channel and only a masked contact detail', async () => {
    await create();

    expect((el('channel-SMS') as HTMLInputElement).checked).toBe(true);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('******3210');
    expect(text).toContain('j***@example.com');
    expect(text).not.toContain('9876543210');
  });

  it('offers no field for an address, a number or a link', async () => {
    await create();

    const inputs = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('input, textarea')) as HTMLInputElement[];
    expect(inputs.every((input) => input.type === 'radio')).toBe(true);
  });

  it('says so when nothing has been chosen yet', async () => {
    get.mockReturnValue(of(prefs({ source: 'DEFAULT', alertChannel: 'EMAIL' })));
    await create();

    expect(el('settings-default-note')?.textContent).toContain('have not chosen yet');
  });

  it('disables a channel the profile cannot serve', async () => {
    get.mockReturnValue(of(prefs({
      alertChannel: 'EMAIL',
      channels: [
        { channel: 'EMAIL', available: true, hint: 'j***@example.com' },
        { channel: 'SMS', available: false, hint: 'No phone number on your profile' },
        { channel: 'PUSH', available: true, hint: 'In-app inbox' }
      ]
    })));
    await create();

    expect((el('channel-SMS') as HTMLInputElement).disabled).toBe(true);
    expect((el('channel-EMAIL') as HTMLInputElement).disabled).toBe(false);
  });

  it('saves the chosen channel and the default account, and confirms', async () => {
    await create();
    update.mockReturnValue(of(prefs({ alertChannel: 'EMAIL' })));

    (el('channel-EMAIL') as HTMLInputElement).click();
    fixture.detectChanges();
    (el('settings-save') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(update).toHaveBeenCalledWith(1, { alertChannel: 'EMAIL', defaultAccountId: 1 });
    expect(el('settings-saved')?.textContent).toContain('next time you sign in');
  });

  it('shows the server\'s own explanation when a channel is refused', async () => {
    await create();
    update.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 422,
      error: { errorCode: 'VAL-422', message: 'There is no phone number on your profile, so SMS alerts are not available' }
    })));

    (el('channel-EMAIL') as HTMLInputElement).click();
    fixture.detectChanges();
    (el('settings-save') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el('settings-error')?.textContent).toContain('no phone number');
    expect(el('settings-saved')).toBeNull();
  });

  it('shows a plain message when the settings cannot be loaded', async () => {
    get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    await create();

    expect(el('settings-load-error')?.textContent).toContain('unavailable');
  });
});
