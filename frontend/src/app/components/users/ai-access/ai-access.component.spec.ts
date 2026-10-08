import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AiConnector } from '../../../core/models/ai-access.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AiAccessService } from '../../../core/services/ai-access.service';
import { AiAccessComponent } from './ai-access.component';

describe('AiAccessComponent', () => {
  let fixture: ComponentFixture<AiAccessComponent>;
  const list = vi.fn();
  const create = vi.fn();
  const revoke = vi.fn();
  const activity = vi.fn();

  const connector = (overrides: Partial<AiConnector> = {}): AiConnector => ({
    id: 'c-1',
    label: 'My Claude',
    scopes: ['READ', 'ALERTS'],
    maxOrderValue: 10000,
    maxOrdersPerDay: 5,
    status: 'ACTIVE',
    createdAt: '2026-10-08T09:00:00Z',
    expiresAt: '2026-11-08T09:00:00Z',
    lastUsedAt: null,
    ...overrides
  });

  const el = <T extends HTMLElement = HTMLElement>(testId: string): T | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  async function build(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AiAccessComponent],
      providers: [
        provideRouter([]),
        { provide: AiAccessService, useValue: { list, create, revoke, activity } },
        { provide: AccountContextService, useValue: { getTokenAccountId: () => 7 } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AiAccessComponent);
    await settle();
  }

  beforeEach(() => {
    list.mockReset().mockReturnValue(of([]));
    create.mockReset();
    revoke.mockReset();
    activity.mockReset().mockReturnValue(of([]));
  });

  it('shows an empty state when no link has been made', async () => {
    await build();
    expect(el('ai-empty')).not.toBeNull();
    expect(list).toHaveBeenCalledWith(7);
  });

  it('lists links with their permissions and a revoke button for active ones only', async () => {
    list.mockReturnValue(of([connector(), connector({ id: 'c-2', label: 'Old', status: 'REVOKED' })]));
    await build();

    expect(text()).toContain('My Claude');
    expect(el('ai-revoke-c-1')).not.toBeNull();
    expect(el('ai-revoke-c-2')).toBeNull();
  });

  it('asks for a name before creating a link', async () => {
    await build();
    el('ai-create-open')!.click();
    await settle();

    el('ai-create')!.click();
    await settle();

    expect(create).not.toHaveBeenCalled();
    expect(el('ai-form-error')).not.toBeNull();
  });

  it('sends read-only plus the chosen permissions and shows the new link once', async () => {
    create.mockReturnValue(of({ connector: connector({ scopes: ['READ', 'ALERTS', 'TRADE'] }), url: 'https://mcp.test/t/etpmcp_x/mcp' }));
    await build();
    el('ai-create-open')!.click();
    await settle();

    const name = el<HTMLInputElement>('ai-label')!;
    name.value = 'My Claude';
    name.dispatchEvent(new Event('input'));
    el<HTMLInputElement>('ai-allow-trade')!.click();
    await settle();
    expect(el('ai-limits')).not.toBeNull();
    el('ai-create')!.click();
    await settle();

    expect(create).toHaveBeenCalledWith(7, {
      label: 'My Claude',
      scopes: ['READ', 'ALERTS', 'TRADE'],
      maxOrderValue: 5000,
      maxOrdersPerDay: 3,
      expiresInDays: 30
    });
    expect(el<HTMLInputElement>('ai-link-value')!.value).toBe('https://mcp.test/t/etpmcp_x/mcp');

    el('ai-link-dismiss')!.click();
    await settle();
    expect(el('ai-new-link')).toBeNull();
    expect(text()).not.toContain('etpmcp_x');
  });

  it('does not send trading limits when trading is not allowed', async () => {
    create.mockReturnValue(of({ connector: connector(), url: 'u' }));
    await build();
    el('ai-create-open')!.click();
    await settle();
    const name = el<HTMLInputElement>('ai-label')!;
    name.value = 'Reader';
    name.dispatchEvent(new Event('input'));
    el('ai-create')!.click();
    await settle();

    const request = create.mock.calls[0][1];
    expect(request.scopes).toEqual(['READ', 'ALERTS']);
    expect(request.maxOrderValue).toBeUndefined();
  });

  it('revokes a link and marks it revoked', async () => {
    list.mockReturnValue(of([connector()]));
    revoke.mockReturnValue(of(undefined));
    await build();

    el('ai-revoke-c-1')!.click();
    await settle();

    expect(revoke).toHaveBeenCalledWith(7, 'c-1');
    expect(el('ai-revoke-c-1')).toBeNull();
  });

  it('says so when the links cannot be loaded', async () => {
    list.mockReturnValue(throwError(() => new Error('boom')));
    await build();
    expect(el('ai-load-error')).not.toBeNull();
  });
});
