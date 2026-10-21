import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import {
  FormField,
  ReadonlyFieldTree,
  form,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideEye, LucideEyeOff } from '@lucide/angular';
import { problemCode, problemFieldErrors } from '@soukly/auth';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import {
  UiAlert,
  UiButton,
  UiCheckbox,
  UiCheckboxControl,
  UiField,
  UiFieldControl,
  UiFieldPrefix,
  UiFieldSuffix,
} from '@soukly/ui';
import { AccountApi } from '../shared/account-api';
import { RegistrationDrafts } from '../shared/registration-draft';
import {
  codeError,
  codePointLength,
  errorKey,
  focusFirstInvalid,
  isEmailFormat,
  tunisianDigits,
} from '../shared/validation';

interface RegisterModel {
  displayName: string;
  email: string;
  phone: string;
  password: string;
  acceptTerms: boolean;
}

/**
 * Registration form (`/{lang}/account/register`): display name, email, phone (+216), password and
 * terms, checked when a field is left and on submit with the rules of AUTH-01. API errors come back
 * as stable codes shown under their field. On `202`, the check-email page receives the address in
 * the navigation state, never in the URL.
 */
@Component({
  selector: 'app-register-page',
  imports: [
    FormField,
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideEye,
    LucideEyeOff,
    UiAlert,
    UiButton,
    UiCheckbox,
    UiCheckboxControl,
    UiField,
    UiFieldControl,
    UiFieldPrefix,
    UiFieldSuffix,
  ],
  templateUrl: './register-page.html',
  styleUrl: '../account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly api = inject(AccountApi);
  private readonly drafts = inject(RegistrationDrafts);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly injector = inject(Injector);
  private readonly generalAlert = viewChild(UiAlert);

  protected readonly model = signal<RegisterModel>({
    displayName: '',
    email: '',
    phone: '',
    password: '',
    acceptTerms: false,
    ...this.drafts.draft(),
  });

  protected readonly form = form(this.model, (path) => {
    required(path.displayName, { error: codeError('displayName.required') });
    validate(path.displayName, ({ value }) => {
      const length = codePointLength(value().trim());
      return value().trim() && (length < 2 || length > 50) ? codeError('displayName.length') : null;
    });
    required(path.email, { error: codeError('email.required') });
    validate(path.email, ({ value }) =>
      value().trim() && !isEmailFormat(value()) ? codeError('email.invalid') : null,
    );
    required(path.phone, { error: codeError('phone.required') });
    validate(path.phone, ({ value }) =>
      value().trim() && !tunisianDigits(value()) ? codeError('phone.invalid') : null,
    );
    required(path.password, { error: codeError('password.required') });
    validate(path.password, ({ value }) => {
      const length = codePointLength(value());
      return value() && (length < 8 || length > 128) ? codeError('password.length') : null;
    });
    validate(path.acceptTerms, ({ value }) => (value() ? null : codeError('terms.required')));
  });

  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly errorKey = errorKey;

  private readonly fieldsInOrder: ReadonlyFieldTree<unknown>[] = [
    this.form.displayName,
    this.form.email,
    this.form.phone,
    this.form.password,
    this.form.acceptTerms,
  ];

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.generalError.set(null);
    this.form().markAsTouched();
    if (this.form().invalid()) {
      focusFirstInvalid(this.fieldsInOrder);
      return;
    }
    this.submitting.set(true);
    await submit(this.form, {
      action: async () => {
        const value = this.model();
        try {
          await this.api.register({
            displayName: value.displayName.trim(),
            email: value.email.trim(),
            phone: `+216${tunisianDigits(value.phone)}`,
            password: value.password,
            acceptTerms: value.acceptTerms,
          });
        } catch (error) {
          return this.apiErrors(error);
        }
        this.drafts.save({
          displayName: value.displayName,
          email: value.email.trim(),
          phone: value.phone,
          acceptTerms: value.acceptTerms,
        });
        await this.router.navigate(['/', this.locale.lang(), 'account', 'check-email'], {
          state: { email: value.email.trim() },
        });
        return undefined;
      },
    });
    this.submitting.set(false);
    // Once the fieldset is enabled again and the errors are rendered.
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

  /** Field codes of a `validation-failed` answer, under their field; anything else on top. */
  private apiErrors(error: unknown) {
    const fields: Record<string, ReadonlyFieldTree<unknown>> = {
      displayName: this.form.displayName,
      email: this.form.email,
      phone: this.form.phone,
      password: this.form.password,
      acceptTerms: this.form.acceptTerms,
    };
    const errors = problemCode(error) === 'validation-failed' ? problemFieldErrors(error) : [];
    const known = errors.filter((item) => fields[item.field]);
    if (known.length === 0) {
      const tooMany = error instanceof HttpErrorResponse && error.status === 429;
      this.generalError.set(
        tooMany ? 'account.errors.too-many-requests' : 'account.errors.unexpected',
      );
      return undefined;
    }
    return known.map((item) => ({ fieldTree: fields[item.field], kind: item.code }));
  }
}
