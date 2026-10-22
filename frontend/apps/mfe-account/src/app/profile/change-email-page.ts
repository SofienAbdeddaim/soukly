import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { FormField, form, required, submit, validate } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideEye, LucideEyeOff, LucideMailCheck } from '@lucide/angular';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import { UiAlert, UiButton, UiField, UiFieldControl, UiFieldSuffix } from '@soukly/ui';
import { AccountToasts } from '../shared/account-toasts';
import { apiFieldErrors, generalErrorKey } from '../shared/api-errors';
import { PageHeader } from '../shared/page-header';
import { codeError, errorKey, focusFirstInvalid, isEmailFormat } from '../shared/validation';
import { ProfileApi } from './profile-api';

/**
 * "Change email" (`/{lang}/account/profile/email`): the new address and the current password. A
 * link valid 24 hours goes to the new address, which replaces the current one only once
 * confirmed; until then the member signs in with the current one. The answer is the same whether
 * the new address already has an account or not.
 */
@Component({
  selector: 'app-change-email-page',
  imports: [
    FormField,
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideEye,
    LucideEyeOff,
    LucideMailCheck,
    UiAlert,
    UiButton,
    UiField,
    UiFieldControl,
    UiFieldSuffix,
    PageHeader,
  ],
  templateUrl: './change-email-page.html',
  styleUrl: '../account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangeEmailPage implements OnInit {
  private readonly api = inject(ProfileApi);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly toasts = inject(AccountToasts);
  private readonly injector = inject(Injector);
  private readonly generalAlert = viewChild(UiAlert);

  /** Current address, shown above the form once the profile is read. */
  protected readonly currentEmail = signal<string | null>(null);
  /** New address the link was sent to: the form is replaced by the "confirm" banner. */
  protected readonly sentTo = signal<string | null>(null);

  protected readonly model = signal({ newEmail: '', currentPassword: '' });
  protected readonly form = form(this.model, (path) => {
    required(path.newEmail, { error: codeError('email.required') });
    validate(path.newEmail, ({ value }) =>
      value().trim() && !isEmailFormat(value()) ? codeError('email.invalid') : null,
    );
    required(path.currentPassword, { error: codeError('current-password.required') });
  });
  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly cancelling = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly errorKey = errorKey;

  private readonly fieldsInOrder = [this.form.newEmail, this.form.currentPassword];

  ngOnInit(): void {
    this.api.get().then(
      (profile) => this.currentEmail.set(profile.email),
      () => this.currentEmail.set(null),
    );
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.generalError.set(null);
    this.form().markAsTouched();
    if (this.form().invalid()) {
      focusFirstInvalid(this.fieldsInOrder);
      return;
    }
    this.submitting.set(true);
    let sent = false;
    await submit(this.form, {
      action: async () => {
        const value = this.model();
        try {
          await this.api.requestEmailChange(value.newEmail.trim(), value.currentPassword);
          sent = true;
          return undefined;
        } catch (error) {
          const fieldErrors = apiFieldErrors(error, {
            newEmail: this.form.newEmail,
            currentPassword: this.form.currentPassword,
          });
          if (fieldErrors.length === 0) {
            this.generalError.set(generalErrorKey(error));
          }
          return fieldErrors;
        }
      },
    });
    this.submitting.set(false);
    if (sent) {
      this.sentTo.set(this.model().newEmail.trim());
      this.model.set({ newEmail: '', currentPassword: '' });
      return;
    }
    afterNextRender(
      () => {
        if (this.form().invalid()) {
          focusFirstInvalid(this.fieldsInOrder);
        } else if (this.generalError()) {
          this.generalAlert()?.focus();
        }
      },
      { injector: this.injector },
    );
  }

  protected async cancelChange(): Promise<void> {
    this.cancelling.set(true);
    this.generalError.set(null);
    try {
      await this.api.cancelEmailChange();
      await this.router.navigateByUrl(`/${this.locale.lang()}/account/profile`);
      this.toasts.success('email-change.cancelled');
    } catch {
      this.generalError.set('account.errors.unexpected');
    } finally {
      this.cancelling.set(false);
    }
  }
}
