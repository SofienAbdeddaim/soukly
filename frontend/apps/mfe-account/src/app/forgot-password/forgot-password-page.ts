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
import { FormField, form, required, validate } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideKeyRound } from '@lucide/angular';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import { UiAlert, UiButton, UiField, UiFieldControl } from '@soukly/ui';
import { AccountApi } from '../shared/account-api';
import { navigationEmail } from '../shared/navigation-email';
import { codeError, errorKey, isEmailFormat } from '../shared/validation';

/**
 * "Forgot your password?" (`/{lang}/account/forgot-password`): asks for a reset link. The email is
 * prefilled when the visitor comes from the login page with an address typed. The answer is the
 * same whether the address has an account or not, so the next page never says which.
 */
@Component({
  selector: 'app-forgot-password-page',
  imports: [
    FormField,
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideKeyRound,
    UiAlert,
    UiButton,
    UiField,
    UiFieldControl,
  ],
  template: `
    <section class="page" aria-labelledby="forgot-title">
      <span class="badge page__center" aria-hidden="true">
        <svg lucideKeyRound [size]="40"></svg>
      </span>
      <div class="page__intro">
        <h1 id="forgot-title" class="page__title">{{ 'account.forgot.title' | transloco }}</h1>
        <p class="page__text">{{ 'account.forgot.text' | transloco }}</p>
      </div>

      @if (generalError(); as key) {
        <ui-alert type="error" data-testid="forgot-error">{{ key | transloco }}</ui-alert>
      }

      <form class="form" novalidate (submit)="onSubmit($event)" data-testid="forgot-form">
        <fieldset class="form__fields" [disabled]="submitting()">
          <ui-field
            [label]="'account.common.email' | transloco"
            [error]="errorKey(form.email) | transloco"
            controlId="forgot-email"
            required
            ltr
          >
            <input
              uiControl
              type="email"
              autocomplete="email"
              inputmode="email"
              spellcheck="false"
              [formField]="form.email"
            />
          </ui-field>
          <button ui-button type="submit" size="lg" [loading]="submitting()">
            {{ 'account.forgot.submit' | transloco }}
          </button>
        </fieldset>
      </form>

      <a class="page__link page__center" [routerLink]="'/account/login' | localePath">
        {{ 'account.forgot.back-to-login' | transloco }}
      </a>
    </section>
  `,
  styleUrl: '../account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPasswordPage {
  private readonly api = inject(AccountApi);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly injector = inject(Injector);
  private readonly generalAlert = viewChild(UiAlert);

  protected readonly model = signal({ email: navigationEmail() ?? '' });
  protected readonly form = form(this.model, (path) => {
    required(path.email, { error: codeError('email.required') });
    validate(path.email, ({ value }) =>
      value().trim() && !isEmailFormat(value()) ? codeError('email.invalid') : null,
    );
  });

  protected readonly submitting = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly errorKey = errorKey;

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.form().markAsTouched();
    if (this.form().invalid()) {
      this.form.email().focusBoundControl();
      return;
    }
    this.submitting.set(true);
    this.generalError.set(null);
    const email = this.model().email.trim();
    try {
      await this.api.forgotPassword(email);
      await this.router.navigate(['/', this.locale.lang(), 'account', 'forgot-password', 'sent'], {
        state: { email },
      });
    } catch (error) {
      this.generalError.set(
        error instanceof HttpErrorResponse && error.status === 429
          ? 'account.errors.too-many-attempts'
          : 'account.errors.unexpected',
      );
      afterNextRender(() => this.generalAlert()?.focus(), { injector: this.injector });
    } finally {
      this.submitting.set(false);
    }
  }
}
