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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideEye, LucideEyeOff } from '@lucide/angular';
import { AuthService, problemCode, retryAfterSeconds, safeReturnUrl } from '@soukly/auth';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import { UiAlert, UiButton, UiField, UiFieldControl, UiFieldSuffix } from '@soukly/ui';
import { ResendEmail } from '../shared/resend-email';
import { codeError, errorKey, focusFirstInvalid, isEmailFormat } from '../shared/validation';

/** General error above the form: never a message of the API, always one of these. */
type LoginError =
  | { kind: 'invalid-credentials' }
  | { kind: 'email-not-verified'; email: string }
  | { kind: 'throttled'; seconds: number }
  | { kind: 'unexpected' };

/**
 * Login form (`/{lang}/account/login?returnUrl=…`). Wrong credentials get one general message,
 * never a message per field, so the page does not tell which one is wrong; the focus moves to it.
 * On success the member goes to the `returnUrl` (internal paths only) or home, through the welcome
 * screen on their very first login.
 */
@Component({
  selector: 'app-login-page',
  imports: [
    FormField,
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideEye,
    LucideEyeOff,
    ResendEmail,
    UiAlert,
    UiButton,
    UiField,
    UiFieldControl,
    UiFieldSuffix,
  ],
  templateUrl: './login-page.html',
  styleUrl: '../account-page.css',
  styles: `
    .login__forgot {
      align-self: flex-start;
      font-size: 0.875rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly locale = inject(LocaleService);
  private readonly injector = inject(Injector);
  private readonly generalAlert = viewChild(UiAlert);

  protected readonly model = signal({ email: '', password: '' });
  protected readonly form = form(this.model, (path) => {
    required(path.email, { error: codeError('email.required') });
    validate(path.email, ({ value }) =>
      value().trim() && !isEmailFormat(value()) ? codeError('email.invalid') : null,
    );
    required(path.password, { error: codeError('password.required') });
  });

  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<LoginError | null>(null);
  protected readonly errorKey = errorKey;

  /** Internal page to go back to after the login, if any. */
  protected readonly returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));

  protected ceilMinutes(seconds: number): number {
    return Math.ceil(seconds / 60);
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.form().markAsTouched();
    if (this.form().invalid()) {
      focusFirstInvalid([this.form.email, this.form.password]);
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const email = this.model().email.trim();
    try {
      const { firstLogin } = await this.auth.login(email, this.model().password);
      await this.goOn(firstLogin);
    } catch (error) {
      this.error.set(loginError(error, email));
      this.model.update((value) => ({ ...value, password: '' }));
      this.form.password().reset();
      afterNextRender(() => this.generalAlert()?.focus(), { injector: this.injector });
    } finally {
      this.submitting.set(false);
    }
  }

  private goOn(firstLogin: boolean): Promise<boolean> {
    const lang = this.locale.lang();
    if (firstLogin) {
      return this.router.navigate(['/', lang, 'account', 'welcome'], {
        queryParams: this.returnUrl ? { returnUrl: this.returnUrl } : {},
      });
    }
    return this.router.navigateByUrl(this.returnUrl ?? `/${lang}`);
  }
}

function loginError(error: unknown, email: string): LoginError {
  if (error instanceof HttpErrorResponse && error.status === 429) {
    // From user-service (failures on this email) or from the per-IP limit of the gateway.
    return { kind: 'throttled', seconds: retryAfterSeconds(error) ?? 60 };
  }
  switch (problemCode(error)) {
    case 'invalid-credentials':
      return { kind: 'invalid-credentials' };
    case 'email-not-verified':
      return { kind: 'email-not-verified', email };
    default:
      return { kind: 'unexpected' };
  }
}
