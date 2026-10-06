import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AlertChannel, PreferencesResponse } from '../../../core/models/preferences.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { PreferencesService } from '../../../core/services/preferences.service';

const CHANNEL_LABELS: Record<AlertChannel, string> = {
  EMAIL: 'E-mail',
  SMS: 'Text message (SMS)',
  PUSH: 'In-app notification'
};

/**
 * The customer's settings: how to reach them, and which account to open on.
 *
 * The page only ever shows a MASKED contact detail and never sends one: the channel is a choice, and
 * where messages go on that channel is read from the profile on the server. There is no field here
 * for an address, a number or a link.
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SettingsComponent {
  private readonly preferencesService = inject(PreferencesService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly preferences = signal<PreferencesResponse | null>(null);

  /** The accounts this sign-in may open on. Usually one; the server refuses any other. */
  protected readonly accounts = computed(() => this.accountContext.getAllowedAccountIds());

  protected readonly form = this.formBuilder.nonNullable.group({
    alertChannel: ['EMAIL' as AlertChannel, [Validators.required]],
    defaultAccountId: [0, [Validators.required, Validators.min(1)]]
  });

  constructor() {
    this.load();
  }

  protected label(channel: AlertChannel): string {
    return CHANNEL_LABELS[channel];
  }

  protected load(): void {
    const accountId = this.accountContext.getTokenAccountId();
    this.loading.set(true);
    this.loadError.set(null);

    this.preferencesService.get(accountId).subscribe({
      next: (preferences) => {
        this.preferences.set(preferences);
        this.form.reset({
          alertChannel: preferences.alertChannel,
          defaultAccountId: preferences.defaultAccountId
        });
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(this.errorMessages.getMessage(error));
        this.loading.set(false);
      }
    });
  }

  protected save(): void {
    this.saved.set(false);
    this.saveError.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }

    this.saving.set(true);
    this.preferencesService
      .update(this.accountContext.getTokenAccountId(), this.form.getRawValue())
      .subscribe({
        next: (preferences) => {
          this.preferences.set(preferences);
          this.form.reset({
            alertChannel: preferences.alertChannel,
            defaultAccountId: preferences.defaultAccountId
          });
          this.saving.set(false);
          this.saved.set(true);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveError.set(this.messageFor(error));
        }
      });
  }

  /** The server explains a refused channel itself ("no phone number on your profile"); everything else uses the shared wording. */
  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 422 && typeof error.error?.message === 'string'
      && error.error.message !== 'Invalid input') {
      return error.error.message;
    }
    return this.errorMessages.getMessage(error);
  }
}
