import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideMail } from '@lucide/angular';
import { LocalePathPipe } from '@soukly/i18n';
import { UiButton } from '@soukly/ui';
import { AccountApi } from '../shared/account-api';
import { navigationEmail } from '../shared/navigation-email';
import { ResendCooldown } from '../shared/resend-cooldown';

/**
 * "Check your email" after a reset request (`/{lang}/account/forgot-password/sent`). The text is
 * neutral ("if an account exists…"). "Resend the link" stays disabled for 60 s after each sending,
 * the first one included. The address comes from the navigation state, never from the URL.
 */
@Component({
  selector: 'app-reset-link-sent-page',
  imports: [RouterLink, TranslocoPipe, LocalePathPipe, LucideMail, UiButton],
  template: `
    <section class="page page--centered" aria-labelledby="sent-title">
      <span class="badge" aria-hidden="true"><svg lucideMail [size]="40"></svg></span>
      <h1 id="sent-title" class="page__title">{{ 'account.forgot-sent.title' | transloco }}</h1>
      @if (email; as address) {
        <p class="page__text">
          {{ 'account.forgot-sent.text-before' | transloco }}
          <strong data-testid="sent-address"
            ><bdi>{{ address }}</bdi></strong
          >{{ 'account.forgot-sent.text-after' | transloco }}
        </p>
        <p class="page__muted">{{ 'account.check-email.spam' | transloco }}</p>
        <button
          ui-button
          type="button"
          variant="secondary"
          class="page__stretch"
          [loading]="sending()"
          [disabled]="cooldown.secondsLeft() > 0"
          data-testid="resend-link"
          (click)="resend(address)"
        >
          @if (cooldown.secondsLeft() > 0) {
            {{
              'account.resend.countdown'
                | transloco
                  : {
                      label: ('account.forgot-sent.resend' | transloco),
                      seconds: cooldown.secondsLeft(),
                    }
            }}
          } @else {
            {{ 'account.forgot-sent.resend' | transloco }}
          }
        </button>
        <p class="status" role="status">
          @if (state() === 'sent') {
            {{ 'account.forgot-sent.resent' | transloco }}
          }
        </p>
        <p class="error" role="alert">
          @if (state() === 'too-many') {
            {{ 'account.errors.too-many-attempts' | transloco }}
          } @else if (state() === 'error') {
            {{ 'account.errors.unexpected' | transloco }}
          }
        </p>
      } @else {
        <p class="page__text">{{ 'account.forgot-sent.no-address' | transloco }}</p>
        <p class="page__muted">{{ 'account.check-email.spam' | transloco }}</p>
        <a class="page__link" [routerLink]="'/account/forgot-password' | localePath">
          {{ 'account.invalid-link.new-link' | transloco }}
        </a>
      }
      <a class="page__link" [routerLink]="'/account/login' | localePath">
        {{ 'account.forgot.back-to-login' | transloco }}
      </a>
    </section>
  `,
  styleUrl: '../account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetLinkSentPage {
  private readonly api = inject(AccountApi);

  protected readonly email = navigationEmail();
  protected readonly cooldown = new ResendCooldown();
  protected readonly sending = signal(false);
  protected readonly state = signal<'idle' | 'sent' | 'too-many' | 'error'>('idle');

  constructor() {
    if (this.email) {
      // The link was just sent by the previous page.
      this.cooldown.start();
    }
  }

  protected async resend(email: string): Promise<void> {
    if (this.cooldown.secondsLeft() > 0) {
      return;
    }
    this.sending.set(true);
    this.state.set('idle');
    try {
      await this.api.forgotPassword(email);
      this.state.set('sent');
      this.cooldown.start();
    } catch (error) {
      this.state.set(
        error instanceof HttpErrorResponse && error.status === 429 ? 'too-many' : 'error',
      );
    } finally {
      this.sending.set(false);
    }
  }
}
