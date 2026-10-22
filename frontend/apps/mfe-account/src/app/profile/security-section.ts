import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocalePathPipe } from '@soukly/i18n';
import { UiAlert } from '@soukly/ui';
import { AccountToasts } from '../shared/account-toasts';
import { Profile, ProfileApi } from './profile-api';

/**
 * "Sign-in and security": the email, with the address waiting for its confirmation and a way to
 * cancel it, and the password, each with a link to change it.
 */
@Component({
  selector: 'app-security-section',
  imports: [RouterLink, TranslocoPipe, LocalePathPipe, UiAlert],
  template: `
    <section class="card" aria-labelledby="profile-security-title">
      <h2 id="profile-security-title" class="card__title">
        {{ 'account.profile.security.title' | transloco }}
      </h2>

      @if (cancelFailed()) {
        <ui-alert type="error">{{ 'account.errors.unexpected' | transloco }}</ui-alert>
      }

      <div class="row">
        <div class="row__text">
          <p class="row__label">{{ 'account.common.email' | transloco }}</p>
          <p class="row__value" dir="ltr" data-testid="profile-email">{{ profile().email }}</p>
          @if (profile().pendingEmail; as pending) {
            <p class="row__pending" data-testid="profile-pending-email">
              {{ 'account.profile.security.pending' | transloco }}
              <bdi>{{ pending }}</bdi>
            </p>
            <button
              type="button"
              class="row__inline-action"
              [disabled]="cancelling()"
              (click)="cancelEmailChange()"
              data-testid="profile-cancel-email-change"
            >
              {{ 'account.profile.security.cancel-change' | transloco }}
            </button>
          }
        </div>
        <a
          class="row__action"
          [routerLink]="'/account/profile/email' | localePath"
          [attr.aria-label]="'account.profile.security.change-email' | transloco"
          data-testid="profile-change-email"
        >
          {{ 'account.profile.security.change' | transloco }}
        </a>
      </div>

      <div class="row">
        <div class="row__text">
          <p class="row__label">{{ 'account.common.password' | transloco }}</p>
          <p class="row__value" aria-hidden="true">••••••••</p>
        </div>
        <a
          class="row__action"
          [routerLink]="'/account/profile/password' | localePath"
          [attr.aria-label]="'account.profile.security.change-password' | transloco"
          data-testid="profile-change-password"
        >
          {{ 'account.profile.security.change' | transloco }}
        </a>
      </div>
    </section>
  `,
  styleUrl: './profile-card.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SecuritySection {
  private readonly api = inject(ProfileApi);
  private readonly toasts = inject(AccountToasts);

  readonly profile = input.required<Profile>();
  /** The pending email change was cancelled. */
  readonly emailChangeCancelled = output<void>();

  protected readonly cancelling = signal(false);
  protected readonly cancelFailed = signal(false);

  protected async cancelEmailChange(): Promise<void> {
    this.cancelling.set(true);
    this.cancelFailed.set(false);
    try {
      await this.api.cancelEmailChange();
      this.emailChangeCancelled.emit();
      this.toasts.success('email-change.cancelled');
    } catch {
      this.cancelFailed.set(true);
    } finally {
      this.cancelling.set(false);
    }
  }
}
