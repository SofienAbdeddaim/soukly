import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { FormField, form, required, submit, validate } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideEye, LucideEyeOff } from '@lucide/angular';
import { LocaleService } from '@soukly/i18n';
import { UiAlert, UiButton, UiField, UiFieldControl, UiFieldSuffix } from '@soukly/ui';
import { AccountToasts } from '../shared/account-toasts';
import { apiFieldErrors, generalErrorKey } from '../shared/api-errors';
import { PageHeader } from '../shared/page-header';
import { codeError, codePointLength, errorKey, focusFirstInvalid } from '../shared/validation';
import { ProfileApi } from './profile-api';

/**
 * "Change password" (`/{lang}/account/profile/password`): the current password, then the new one
 * with the rules of AUTH-01. Every other device is signed out; this one stays signed in and goes
 * back to the profile with a toast.
 */
@Component({
  selector: 'app-change-password-page',
  imports: [
    FormField,
    TranslocoPipe,
    LucideEye,
    LucideEyeOff,
    UiAlert,
    UiButton,
    UiField,
    UiFieldControl,
    UiFieldSuffix,
    PageHeader,
  ],
  templateUrl: './change-password-page.html',
  styleUrl: '../account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangePasswordPage {
  private readonly api = inject(ProfileApi);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly toasts = inject(AccountToasts);
  private readonly injector = inject(Injector);
  private readonly generalAlert = viewChild(UiAlert);

  protected readonly model = signal({ currentPassword: '', newPassword: '' });
  protected readonly form = form(this.model, (path) => {
    required(path.currentPassword, { error: codeError('current-password.required') });
    required(path.newPassword, { error: codeError('password.required') });
    validate(path.newPassword, ({ value }) => {
      const length = codePointLength(value());
      return value() && (length < 8 || length > 128) ? codeError('password.length') : null;
    });
  });
  protected readonly showCurrent = signal(false);
  protected readonly showNew = signal(false);
  protected readonly submitting = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly errorKey = errorKey;

  private readonly fieldsInOrder = [this.form.currentPassword, this.form.newPassword];

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.generalError.set(null);
    this.form().markAsTouched();
    if (this.form().invalid()) {
      focusFirstInvalid(this.fieldsInOrder);
      return;
    }
    this.submitting.set(true);
    let changed = false;
    await submit(this.form, {
      action: async () => {
        const value = this.model();
        try {
          await this.api.changePassword(value.currentPassword, value.newPassword);
          changed = true;
          return undefined;
        } catch (error) {
          const fieldErrors = apiFieldErrors(error, {
            currentPassword: this.form.currentPassword,
            newPassword: this.form.newPassword,
          });
          if (fieldErrors.length === 0) {
            this.generalError.set(generalErrorKey(error));
          }
          return fieldErrors;
        }
      },
    });
    this.submitting.set(false);
    if (changed) {
      await this.router.navigateByUrl(`/${this.locale.lang()}/account/profile`);
      this.toasts.success('password-change.changed');
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
}
