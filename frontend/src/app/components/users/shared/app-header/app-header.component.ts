import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { switchMap, timer } from 'rxjs';
import { AuthService } from '../../../../core/auth/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { BrandLogoComponent } from '../brand-logo/brand-logo.component';
import { ThemeToggleComponent } from '../theme-toggle/theme-toggle.component';

interface NavigationItem {
  label: string;
  path: string;
  /** Show the unread-message count beside this item. */
  unread?: boolean;
}

/** How often the unread badge looks for new messages. */
export const UNREAD_REFRESH_MS = 30_000;

@Component({
  selector: 'app-app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, ThemeToggleComponent, BrandLogoComponent],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppHeaderComponent {
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly mustChangePassword = this.auth.mustChangePassword;
  protected readonly unread = this.notifications.unread;

  private readonly customerItems: NavigationItem[] = [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Portfolio', path: '/portfolio' },
    { label: 'Order ticket', path: '/order-ticket' },
    { label: 'Watchlist', path: '/watchlist' },
    { label: 'Alerts', path: '/alerts' },
    { label: 'Blotter', path: '/blotter' },
    { label: 'Inbox', path: '/notifications', unread: true },
    { label: 'Account', path: '/account' },
    { label: 'Settings', path: '/settings' }
  ];

  private readonly adminItems: NavigationItem[] = [{ label: 'Admin', path: '/admin' }];

  protected readonly navigationItems = computed(() => (this.auth.isAdmin() ? this.adminItems : this.customerItems));

  constructor() {
    // Keep the inbox badge fresh while the customer works. An admin has no inbox.
    if (!this.auth.isAdmin()) {
      timer(0, UNREAD_REFRESH_MS)
        .pipe(
          switchMap(() => this.notifications.refreshUnread()),
          takeUntilDestroyed()
        )
        .subscribe();
    }
  }

  protected signOut(): void {
    this.auth.logout();
  }
}
