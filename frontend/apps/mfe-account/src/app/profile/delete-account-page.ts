import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { FormField, form, required, submit, validate } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideEye, LucideEyeOff } from '@lucide/angular';
import { AuthService } from '@soukly/auth';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import { ListingsApi } from '@soukly/listing-data';
import {
  UiAlert,
  UiButton,
  UiCheckbox,
  UiCheckboxControl,
  UiField,
  UiFieldControl,
  UiFieldSuffix,
} from '@soukly/ui';
import { AccountToasts } from '../shared/account-toasts';
import { apiFieldErrors, generalErrorKey } from '../shared/api-errors';
import { PageHeader } from '../shared/page-header';
import { pluralKey } from '../shared/plural';
import { codeError, errorKey, focusFirstInvalid } from '../shared/validation';
import { ProfileApi } from './profile-api';

/**
 * "Delete my account" (`/{lang}/account/profile/delete`): what will happen, the password and a
 * required confirmation box. On success the session is forgotten and the visitor goes home with a
 * toast; user-service has already signed out every device and cleared the refresh cookie.
 */
@Component({
  selector: 'app-delete-account-page',
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
    UiFieldSuffix,
    PageHeader,
  ],
  templateUrl: './delete-account-page.html',
  styleUrls: ['../account-page.css', './delete-account-page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeleteAccountPage implements OnInit {
  private readonly api = inject(ProfileApi);
  private readonly listings = inject(ListingsApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly toasts = inject(AccountToasts);
  private readonly injector = inject(Injector);
  private readonly generalAlert = viewChild(UiAlert);

  /** Listings of the member in every state, drafts included; null until known (or unknown). */
  protected readonly listingCount = signal<number | null>(null);
  protected readonly listingsKey = computed(() => {
    const count = this.listingCount();
    return count === null
      ? 'account.delete.consequences.listings-unknown'
      : pluralKey('account.delete.consequences.listings', count, this.locale.intlLocale());
  });

  protected readonly model = signal({ currentPassword: '', understood: false });
  protected readonly form = form(this.model, (path) => {
    required(path.currentPassword, { error: codeError('current-password.required') });
    validate(path.understood, ({ value }) => (value() ? null : codeError('delete.understood')));
  });
  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly errorKey = errorKey;

  private readonly fieldsInOrder = [this.form.currentPassword, this.form.understood];

  ngOnInit(): void {
    // The counts of every tab come with any first page of "My listings".
    this.listings.mine('PUBLISHED', undefined, 1).then(
      (page) =>
        this.listingCount.set(
          page.counts ? Object.values(page.counts).reduce((sum, count) => sum + count, 0) : null,
        ),
      () => this.listingCount.set(null),
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
    let deleted = false;
    await submit(this.form, {
      action: async () => {
        try {
          await this.api.deleteAccount(this.model().currentPassword);
          deleted = true;
          return undefined;
        } catch (error) {
          const fieldErrors = apiFieldErrors(error, {
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
    if (deleted) {
      this.auth.clear();
      await this.router.navigateByUrl(`/${this.locale.lang()}`);
      this.toasts.success('delete.deleted');
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
