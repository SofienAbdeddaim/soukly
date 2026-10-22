import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideMail } from '@lucide/angular';
import { LocalePathPipe } from '@soukly/i18n';
import { navigationEmail } from '../shared/navigation-email';
import { ResendEmail } from '../shared/resend-email';

/**
 * "Check your email" (`/{lang}/account/check-email`), after a registration. The address comes from
 * the navigation state (kept by the browser history on reload), never from the URL.
 */
@Component({
  selector: 'app-check-email-page',
  imports: [RouterLink, TranslocoPipe, LocalePathPipe, LucideMail, ResendEmail],
  template: `
    <section class="page page--centered" aria-labelledby="check-email-title">
      <span class="badge" aria-hidden="true"><svg lucideMail [size]="40"></svg></span>
      <h1 id="check-email-title" class="page__title">
        {{ 'account.check-email.title' | transloco }}
      </h1>
      @if (email; as address) {
        <p class="page__text">
          {{ 'account.check-email.sent-to' | transloco }}
          <strong data-testid="check-email-address"
            ><bdi>{{ address }}</bdi></strong
          >
        </p>
        <p class="page__text">{{ 'account.check-email.validity' | transloco }}</p>
        <p class="page__muted">{{ 'account.check-email.spam' | transloco }}</p>
        <app-resend-email [email]="address" />
        <a class="page__link" [routerLink]="'/account/register' | localePath">
          {{ 'account.check-email.change-email' | transloco }}
        </a>
      } @else {
        <p class="page__text">{{ 'account.check-email.no-address' | transloco }}</p>
        <p class="page__muted">{{ 'account.check-email.spam' | transloco }}</p>
      }
    </section>
  `,
  styleUrl: '../account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckEmailPage {
  protected readonly email = navigationEmail();
}
