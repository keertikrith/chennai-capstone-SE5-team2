import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NotificationItem } from '../../../core/models/notification.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { NotificationService } from '../../../core/services/notification.service';
import { NotificationsComponent } from './notifications.component';

describe('NotificationsComponent', () => {
  let fixture: ComponentFixture<NotificationsComponent>;
  const list = vi.fn();
  const markRead = vi.fn();

  const item = (overrides: Partial<NotificationItem> = {}): NotificationItem => ({
    id: 1,
    kind: 'ORDER_FILLED',
    subject: 'Order filled: BUY 100 AAPL',
    body: 'Your BUY order for 100 AAPL was filled at 232.71.',
    status: 'SENT',
    statusReason: null,
    channel: 'SMS',
    channelSource: 'STORED',
    createdAt: '2026-10-06T09:14:24Z',
    sentAt: '2026-10-06T09:14:30Z',
    read: false,
    ...overrides
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const el = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        { provide: NotificationService, useValue: { list, markRead } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    list.mockReset().mockReturnValue(of([item()]));
    markRead.mockReset();
  });

  it('shows a fill, and the channel it was sent on', async () => {
    await create();

    expect(text()).toContain('Order filled: BUY 100 AAPL');
    expect(el('notification-delivery')?.textContent).toContain('Sent by text message');
  });

  it('shows a rejection: a customer who only hears about successes does not know their order failed', async () => {
    list.mockReturnValue(of([item({ id: 2, kind: 'ORDER_REJECTED', subject: 'Order rejected: BUY 100 AAPL',
      body: 'Your BUY order for 100 AAPL was not carried out because there was not enough cash in your account.' })]));
    await create();

    expect(text()).toContain('Order rejected');
    expect(text()).toContain('not enough cash');
  });

  it('says when a message went out on the default channel because none was chosen', async () => {
    list.mockReturnValue(of([item({ channel: 'EMAIL', channelSource: 'DEFAULT' })]));
    await create();

    expect(el('notification-delivery')?.textContent).toContain('your default');
  });

  it('is honest that a stub channel only recorded the message', async () => {
    list.mockReturnValue(of([item({ statusReason: 'STUB_DELIVERY' })]));
    await create();

    expect(el('notification-delivery')?.textContent).toContain('No text message provider is connected');
  });

  it('explains a message held for want of a contact detail', async () => {
    list.mockReturnValue(of([item({ status: 'HELD', statusReason: 'NO_CONTACT', channel: null, sentAt: null })]));
    await create();

    expect(el('notification-delivery')?.textContent).toContain('On hold');
    expect(el('notification-delivery')?.textContent).toContain('Settings');
  });

  it('explains a message still waiting and one that gave up', async () => {
    list.mockReturnValue(of([
      item({ id: 3, status: 'QUEUED', channel: null, sentAt: null }),
      item({ id: 4, status: 'FAILED', statusReason: 'PROVIDER_ERROR', channel: 'EMAIL' })
    ]));
    await create();

    const text = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="notification-delivery"]'))
      .map((node) => node.textContent);
    expect(text[0]).toContain('Waiting to be sent');
    expect(text[1]).toContain('could not send');
  });

  it('marks a message read', async () => {
    await create();
    markRead.mockReturnValue(of(item({ read: true })));

    (el('mark-read') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(markRead).toHaveBeenCalledWith(1, 1);
    expect(el('mark-read')).toBeNull();
  });

  it('filters to what is unread', async () => {
    list.mockReturnValue(of([item({ id: 1, read: true, subject: 'Old news' }), item({ id: 2, subject: 'Fresh news' })]));
    await create();

    (el('filter-unread') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(text()).toContain('Fresh news');
    expect(text()).not.toContain('Old news');
  });

  it('welcomes a customer with no messages', async () => {
    list.mockReturnValue(of([]));
    await create();

    expect(el('notifications-empty')?.textContent).toContain('No messages yet');
  });

  it('shows a plain message when the inbox cannot be loaded', async () => {
    list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    await create();

    expect(el('notifications-error')?.textContent).toContain('unavailable');
  });
});
