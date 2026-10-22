import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import { SocialConnection } from '@soukly/social-data';
import { UiButton } from '@soukly/ui';
import { NetworkCard } from './network-card';

/**
 * Instagram card of "My networks" (SOC-02): "Not connected" with "Connect" and the professional
 * account requirement; once connected "@username", its connection date, "Change account" and
 * "Disconnect"; when Instagram refused the token (`NEEDS_RECONNECT`), a message and "Connect
 * again".
 */
@Component({
  selector: 'app-instagram-card',
  imports: [TranslocoPipe, UiButton, NetworkCard],
  template: `
    @if (connection(); as account) {
      @if (account.status === 'NEEDS_RECONNECT') {
        <app-network-card
          network="instagram"
          [name]="account.displayName"
          [status]="'account.social.instagram.needs-reconnect' | transloco"
          [attention]="true"
          data-testid="instagram-card"
          data-state="needs-reconnect"
        >
          <button
            ui-button
            type="button"
            [loading]="busy()"
            data-testid="instagram-reconnect"
            (click)="connectRequested.emit()"
          >
            {{ 'account.social.instagram.reconnect' | transloco }}
          </button>
        </app-network-card>
      } @else {
        <app-network-card
          network="instagram"
          [name]="account.displayName"
          [status]="'account.social.instagram.connected-on' | transloco: { date: connectedDate() }"
          data-testid="instagram-card"
          data-state="connected"
        >
          <div class="instagram-card__actions">
            <button
              ui-button
              variant="ghost"
              type="button"
              [loading]="busy()"
              data-testid="instagram-change"
              (click)="connectRequested.emit()"
            >
              {{ 'account.social.instagram.change' | transloco }}
            </button>
            <button
              ui-button
              variant="ghost"
              type="button"
              class="instagram-card__disconnect"
              data-testid="instagram-disconnect"
              (click)="disconnectRequested.emit()"
            >
              {{ 'account.social.instagram.disconnect' | transloco }}
            </button>
          </div>
        </app-network-card>
      }
    } @else {
      <app-network-card
        network="instagram"
        [name]="'account.social.instagram.name' | transloco"
        [status]="'account.social.instagram.not-connected' | transloco"
        data-testid="instagram-card"
        data-state="not-connected"
      >
        <button
          ui-button
          cardAction
          type="button"
          [loading]="busy()"
          aria-describedby="network-card-instagram"
          data-testid="instagram-connect"
          (click)="connectRequested.emit()"
        >
          {{ 'account.social.connect' | transloco }}
        </button>
        <p class="instagram-card__hint">{{ 'account.social.instagram.hint' | transloco }}</p>
      </app-network-card>
    }
  `,
  styles: `
    .instagram-card__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .instagram-card__actions > * {
      flex: 1 1 auto;
    }

    .instagram-card__disconnect {
      color: var(--color-primary);
    }

    .instagram-card__hint {
      color: var(--color-text-muted);
      font-size: 0.8125rem;
      line-height: 1.45;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstagramCard {
  private readonly locale = inject(LocaleService);

  readonly connection = input<SocialConnection | null>(null);
  /** A connection is starting: the buttons that open Instagram wait. */
  readonly busy = input(false);
  readonly connectRequested = output<void>();
  readonly disconnectRequested = output<void>();

  /** Day and month of the connection in the language of the page: "12 oct.", "Oct 12". */
  protected readonly connectedDate = computed(() => {
    const account = this.connection();
    return account
      ? new Intl.DateTimeFormat(this.locale.intlLocale(), {
          day: 'numeric',
          month: 'short',
        }).format(new Date(account.connectedAt))
      : '';
  });
}
