import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiButton } from '@soukly/ui';
import { AccountApi } from './account-api';
import { ResendCooldown } from './resend-cooldown';

/**
 * "Resend the email" button: asks user-service for a new verification link, then stays disabled
 * for 60 s with a countdown. The confirmation is announced politely.
 */
@Component({
  selector: 'app-resend-email',
  imports: [TranslocoPipe, UiButton],
  template: `
    <button
      ui-button
      type="button"
      variant="secondary"
      [loading]="sending()"
      [disabled]="cooldown.secondsLeft() > 0"
      data-testid="resend-email"
      (click)="resend()"
    >
      @if (cooldown.secondsLeft() > 0) {
        {{
          'account.resend.countdown'
            | transloco: { label: (label() | transloco), seconds: cooldown.secondsLeft() }
        }}
      } @else {
        {{ label() | transloco }}
      }
    </button>
    <p class="resend__status" role="status">
      @if (state() === 'sent') {
        {{ 'account.resend.sent' | transloco }}
      }
    </p>
    <p class="resend__error" role="alert">
      @if (state() === 'error') {
        {{ 'account.errors.unexpected' | transloco }}
      }
    </p>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-self: stretch;
      gap: 8px;
    }

    .resend__status,
    .resend__error {
      margin: 0;
      font-size: 0.875rem;
      font-weight: 500;
    }

    .resend__status {
      color: var(--color-success);
    }

    .resend__error {
      color: var(--color-danger);
    }

    .resend__status:empty,
    .resend__error:empty {
      display: none;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResendEmail {
  private readonly api = inject(AccountApi);

  /** Address the link is sent to. */
  readonly email = input.required<string>();
  /** Translation key of the button. */
  readonly label = input('account.resend.button');

  protected readonly cooldown = new ResendCooldown();
  protected readonly sending = signal(false);
  protected readonly state = signal<'idle' | 'sent' | 'error'>('idle');

  protected async resend(): Promise<void> {
    if (this.cooldown.secondsLeft() > 0) {
      return;
    }
    this.sending.set(true);
    this.state.set('idle');
    try {
      await this.api.resendVerification(this.email());
      this.state.set('sent');
      this.cooldown.start();
    } catch {
      this.state.set('error');
    } finally {
      this.sending.set(false);
    }
  }
}
