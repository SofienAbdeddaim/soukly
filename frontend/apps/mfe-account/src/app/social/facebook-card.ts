import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import { SocialConnection } from '@soukly/social-data';
import { UiButton, UiSwitch } from '@soukly/ui';
import { NetworkCard } from './network-card';

/**
 * Facebook Page card of "My networks" (SOC-01): "Not connected" with "Connect"; once connected the
 * Page name, its connection date, "Change Page", "Disconnect" and the switch "tick by default for
 * every new listing" (SOC-03); when Facebook refused the token
 * (`NEEDS_RECONNECT`), a message and "Connect again".
 */
@Component({
  selector: 'app-facebook-card',
  imports: [TranslocoPipe, UiButton, UiSwitch, NetworkCard],
  template: `
    @if (connection(); as page) {
      @if (page.status === 'NEEDS_RECONNECT') {
        <app-network-card
          network="facebook"
          [name]="page.displayName"
          [status]="'account.social.facebook.needs-reconnect' | transloco"
          [attention]="true"
          data-testid="facebook-card"
          data-state="needs-reconnect"
        >
          <button
            ui-button
            type="button"
            [loading]="busy()"
            data-testid="facebook-reconnect"
            (click)="connectRequested.emit()"
          >
            {{ 'account.social.facebook.reconnect' | transloco }}
          </button>
        </app-network-card>
      } @else {
        <app-network-card
          network="facebook"
          [name]="page.displayName"
          [status]="'account.social.facebook.connected-on' | transloco: { date: connectedDate() }"
          data-testid="facebook-card"
          data-state="connected"
        >
          <div class="facebook-card__actions">
            <button
              ui-button
              variant="ghost"
              type="button"
              [loading]="busy()"
              data-testid="facebook-change"
              (click)="connectRequested.emit()"
            >
              {{ 'account.social.facebook.change' | transloco }}
            </button>
            <button
              ui-button
              variant="ghost"
              type="button"
              class="facebook-card__disconnect"
              data-testid="facebook-disconnect"
              (click)="disconnectRequested.emit()"
            >
              {{ 'account.social.facebook.disconnect' | transloco }}
            </button>
          </div>
          <ui-switch
            class="facebook-card__auto-share"
            data-testid="facebook-auto-share"
            [checked]="page.autoShare"
            [busy]="autoShareBusy()"
            (checkedChange)="autoShareChange.emit($event)"
          >
            {{ 'account.social.auto-share' | transloco }}
          </ui-switch>
        </app-network-card>
      }
    } @else {
      <app-network-card
        network="facebook"
        [name]="'account.social.facebook.name' | transloco"
        [status]="'account.social.not-connected' | transloco"
        data-testid="facebook-card"
        data-state="not-connected"
      >
        <button
          ui-button
          cardAction
          type="button"
          [loading]="busy()"
          aria-describedby="network-card-facebook"
          data-testid="facebook-connect"
          (click)="connectRequested.emit()"
        >
          {{ 'account.social.connect' | transloco }}
        </button>
        <p class="facebook-card__hint">{{ 'account.social.facebook.hint' | transloco }}</p>
      </app-network-card>
    }
  `,
  styles: `
    .facebook-card__auto-share {
      padding-block-start: 10px;
      border-block-start: 1px solid var(--color-border);
    }

    .facebook-card__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .facebook-card__actions > * {
      flex: 1 1 auto;
    }

    .facebook-card__disconnect {
      color: var(--color-primary);
    }

    .facebook-card__hint {
      color: var(--color-text-muted);
      font-size: 0.8125rem;
      line-height: 1.45;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FacebookCard {
  private readonly locale = inject(LocaleService);

  readonly connection = input<SocialConnection | null>(null);
  /** A connection is starting: the buttons that open Facebook wait. */
  readonly busy = input(false);
  readonly connectRequested = output<void>();
  readonly disconnectRequested = output<void>();
  /** The preference "tick by default for every new listing" is being saved (SOC-03). */
  readonly autoShareBusy = input(false);
  readonly autoShareChange = output<boolean>();

  /** Day and month of the connection in the language of the page: "12 oct.", "Oct 12". */
  protected readonly connectedDate = computed(() => {
    const page = this.connection();
    return page
      ? new Intl.DateTimeFormat(this.locale.intlLocale(), {
          day: 'numeric',
          month: 'short',
        }).format(new Date(page.connectedAt))
      : '';
  });
}
