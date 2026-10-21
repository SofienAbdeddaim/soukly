import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormField, form, required, validate } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideCircleCheck, LucideLink2Off } from '@lucide/angular';
import { problemCode } from '@soukly/auth';
import { LocalePathPipe } from '@soukly/i18n';
import { UiAlert, UiButton, UiField, UiFieldControl, UiSkeleton } from '@soukly/ui';
import { AccountApi } from '../shared/account-api';
import { ResendCooldown } from '../shared/resend-cooldown';
import { codeError, errorKey, isEmailFormat } from '../shared/validation';

type VerifyState = 'checking' | 'activated' | 'invalid' | 'error';

/**
 * Link of the verification email (`/{lang}/account/verify-email?token=…`): verifies the token as
 * soon as the page opens, then says the account is active, or offers a new link when the token is
 * unknown, expired or already used. The token is removed from the address bar once read.
 */
@Component({
  selector: 'app-verify-email-page',
  imports: [
    FormField,
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideCircleCheck,
    LucideLink2Off,
    UiAlert,
    UiButton,
    UiField,
    UiFieldControl,
    UiSkeleton,
  ],
  templateUrl: './verify-email-page.html',
  styleUrl: '../account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyEmailPage implements OnInit {
  private readonly api = inject(AccountApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private token: string | null = null;

  protected readonly state = signal<VerifyState>('checking');

  protected readonly resendModel = signal({ email: '' });
  protected readonly resendForm = form(this.resendModel, (path) => {
    required(path.email, { error: codeError('email.required') });
    validate(path.email, ({ value }) =>
      value().trim() && !isEmailFormat(value()) ? codeError('email.invalid') : null,
    );
  });
  protected readonly sending = signal(false);
  protected readonly resendState = signal<'idle' | 'sent' | 'error'>('idle');
  protected readonly cooldown = new ResendCooldown();
  protected readonly errorKey = errorKey;

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (this.token) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { token: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
    void this.verify();
  }

  protected async verify(): Promise<void> {
    if (!this.token) {
      this.state.set('invalid');
      return;
    }
    this.state.set('checking');
    try {
      await this.api.verifyEmail(this.token);
      this.state.set('activated');
    } catch (error) {
      this.state.set(problemCode(error) === 'verification-token-invalid' ? 'invalid' : 'error');
    }
  }

  protected async resend(event: Event): Promise<void> {
    event.preventDefault();
    this.resendForm().markAsTouched();
    if (this.resendForm().invalid()) {
      this.resendForm.email().focusBoundControl();
      return;
    }
    if (this.cooldown.secondsLeft() > 0) {
      return;
    }
    this.sending.set(true);
    this.resendState.set('idle');
    try {
      await this.api.resendVerification(this.resendModel().email.trim());
      this.resendState.set('sent');
      this.cooldown.start();
    } catch {
      this.resendState.set('error');
    } finally {
      this.sending.set(false);
    }
  }
}
