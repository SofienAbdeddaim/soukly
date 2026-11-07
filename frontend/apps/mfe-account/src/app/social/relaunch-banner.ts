import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import { BlockedListings } from '@soukly/social-data';
import { UiAlert, UiButton } from '@soukly/ui';
import { pluralKey } from '../shared/plural';

/** Titles listed in the banner; the others are counted ("and 2 others"). */
export const RELAUNCH_TITLES_SHOWN = 5;

/**
 * Offered on "My networks" after a network is connected again (SOC-06): the listings still online
 * whose post was blocked while it was not connected, to post them in one click. "No thanks"
 * changes nothing: they stay "to connect again", then can be retried one by one.
 */
@Component({
  selector: 'app-relaunch-banner',
  imports: [TranslocoPipe, UiAlert, UiButton],
  template: `
    @let name = 'account.posts.network.' + blocked().network | transloco;
    <div class="relaunch" role="status" data-testid="relaunch-banner">
      <p>
        <strong>{{ 'account.social.relaunch.title.' + blocked().network | transloco }}</strong>
        {{ textKey() | transloco: { count: blocked().total, network: name } }}
      </p>
      <ul class="relaunch__titles" data-testid="relaunch-titles">
        @for (listing of shown(); track listing.listingId) {
          <li>
            <bdi>{{ listing.title ?? ('account.listings.untitled' | transloco) }}</bdi>
          </li>
        }
        @if (others() > 0) {
          <li class="relaunch__others">
            {{ othersKey() | transloco: { count: others() } }}
          </li>
        }
      </ul>
      @if (failed()) {
        <ui-alert type="error" data-testid="relaunch-error">
          {{ 'account.social.relaunch.error' | transloco }}
        </ui-alert>
      }
      <div class="relaunch__actions">
        <button
          ui-button
          type="button"
          [loading]="busy()"
          data-testid="relaunch-confirm"
          (click)="confirmed.emit()"
        >
          {{ confirmKey() | transloco: { count: blocked().total } }}
        </button>
        <button
          ui-button
          variant="ghost"
          type="button"
          [disabled]="busy()"
          data-testid="relaunch-dismiss"
          (click)="dismissed.emit()"
        >
          {{ 'account.social.relaunch.dismiss' | transloco }}
        </button>
      </div>
      <p class="relaunch__note">{{ 'account.social.relaunch.note' | transloco }}</p>
    </div>
  `,
  styles: `
    .relaunch {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 14px;
      border-radius: var(--radius-lg);
      background-color: var(--color-success-surface);
      font-size: 0.875rem;
      line-height: 1.45;
    }

    .relaunch__titles {
      display: flex;
      flex-direction: column;
      gap: 2px;
      margin: 0;
      padding-inline-start: 18px;
    }

    .relaunch__others {
      color: var(--color-text-muted);
      list-style: none;
    }

    .relaunch__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .relaunch__note {
      color: var(--color-text-muted);
      font-size: 0.75rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RelaunchBanner {
  readonly blocked = input.required<BlockedListings>();
  readonly busy = input(false);
  readonly failed = input(false);
  readonly confirmed = output<void>();
  readonly dismissed = output<void>();

  private readonly locale = inject(LocaleService);

  protected readonly shown = computed(() =>
    this.blocked().listings.slice(0, RELAUNCH_TITLES_SHOWN),
  );
  protected readonly others = computed(() => this.blocked().total - this.shown().length);
  protected readonly textKey = computed(() => this.plural('text', this.blocked().total));
  protected readonly confirmKey = computed(() => this.plural('confirm', this.blocked().total));
  protected readonly othersKey = computed(() => this.plural('others', this.others()));

  private plural(key: string, count: number): string {
    return pluralKey(`account.social.relaunch.${key}`, count, this.locale.lang());
  }
}
