import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  inject,
  Injector,
  input,
  OnInit,
  output,
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
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '@soukly/auth';
import { Lang, LANG_NAMES, SUPPORTED_LANGS } from '@soukly/i18n';
import { UiAlert, UiButton, UiField, UiFieldControl, UiFieldPrefix } from '@soukly/ui';
import { AccountToasts } from '../shared/account-toasts';
import { apiFieldErrors, generalErrorKey } from '../shared/api-errors';
import {
  codeError,
  codePointLength,
  errorKey,
  focusFirstInvalid,
  nationalPhone,
  tunisianDigits,
} from '../shared/validation';
import { Profile, ProfileApi } from './profile-api';

interface InfoModel {
  displayName: string;
  phone: string;
  preferredLanguage: Lang;
}

/**
 * "My details": display name, phone (+216) and language of the emails, with the rules of the
 * registration (AUTH-01). The new phone shows at once on every listing (CATA-03 reads it when a
 * number is revealed); the interface keeps the language of the URL.
 */
@Component({
  selector: 'app-profile-info-form',
  imports: [FormField, TranslocoPipe, UiAlert, UiButton, UiField, UiFieldControl, UiFieldPrefix],
  templateUrl: './profile-info-form.html',
  styleUrl: './profile-card.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileInfoForm implements OnInit {
  private readonly api = inject(ProfileApi);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(AccountToasts);
  private readonly injector = inject(Injector);
  private readonly generalAlert = viewChild(UiAlert);

  readonly profile = input.required<Profile>();
  readonly saved = output<Profile>();

  protected readonly languages = SUPPORTED_LANGS.map((code) => ({ code, name: LANG_NAMES[code] }));
  protected readonly model = signal<InfoModel>({
    displayName: '',
    phone: '',
    preferredLanguage: 'fr',
  });
  protected readonly form = form(this.model, (path) => {
    required(path.displayName, { error: codeError('displayName.required') });
    validate(path.displayName, ({ value }) => {
      const length = codePointLength(value().trim());
      return value().trim() && (length < 2 || length > 50) ? codeError('displayName.length') : null;
    });
    required(path.phone, { error: codeError('phone.required') });
    validate(path.phone, ({ value }) =>
      value().trim() && !tunisianDigits(value()) ? codeError('phone.invalid') : null,
    );
  });
  protected readonly submitting = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly errorKey = errorKey;

  private readonly fieldsInOrder: ReadonlyFieldTree<unknown>[] = [
    this.form.displayName,
    this.form.phone,
    this.form.preferredLanguage,
  ];

  ngOnInit(): void {
    const profile = this.profile();
    this.model.set({
      displayName: profile.displayName,
      phone: nationalPhone(profile.phone),
      preferredLanguage: profile.preferredLanguage,
    });
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
    let updated = null as Profile | null;
    await submit(this.form, {
      action: async () => {
        const value = this.model();
        try {
          updated = await this.api.update({
            displayName: value.displayName.trim(),
            phone: `+216${tunisianDigits(value.phone)}`,
            preferredLanguage: value.preferredLanguage,
          });
          return undefined;
        } catch (error) {
          const fieldErrors = apiFieldErrors(error, {
            displayName: this.form.displayName,
            phone: this.form.phone,
            preferredLanguage: this.form.preferredLanguage,
          });
          if (fieldErrors.length === 0) {
            this.generalError.set(generalErrorKey(error));
          }
          return fieldErrors;
        }
      },
    });
    this.submitting.set(false);
    if (updated) {
      this.onSaved(updated);
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

  private onSaved(profile: Profile): void {
    this.model.set({
      displayName: profile.displayName,
      phone: nationalPhone(profile.phone),
      preferredLanguage: profile.preferredLanguage,
    });
    this.auth.updateUser({
      displayName: profile.displayName,
      preferredLanguage: profile.preferredLanguage,
    });
    this.saved.emit(profile);
    this.toasts.success('profile.saved');
  }
}
