import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Card of one social network on "My networks" (SOC-01): its logo, its name, a status line and
 * the actions projected by the network card that uses it. `attention` outlines a card that needs
 * the member (connect again).
 */
@Component({
  selector: 'app-network-card',
  template: `
    <div class="network-card__head">
      <span
        class="network-card__logo"
        [class]="'network-card__logo--' + network()"
        aria-hidden="true"
      >
        {{ network() === 'facebook' ? 'f' : 'IG' }}
      </span>
      <div class="network-card__text">
        <p class="network-card__name" [id]="nameId()">{{ name() }}</p>
        <p class="network-card__status" [class.network-card__status--attention]="attention()">
          {{ status() }}
        </p>
      </div>
      <ng-content select="[cardAction]" />
    </div>
    <ng-content />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 14px;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      background-color: var(--color-surface);
    }

    :host(.network-card--attention) {
      border-color: var(--color-warning-border);
    }

    :host(.network-card--disabled) {
      background-color: var(--color-surface-muted);
    }

    .network-card__head {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .network-card__logo {
      display: grid;
      flex: none;
      place-items: center;
      inline-size: 44px;
      block-size: 44px;
      border-radius: var(--radius-md);
      color: var(--color-on-network);
      font-size: 1.125rem;
      font-weight: 600;
    }

    .network-card__logo--facebook {
      background-color: var(--color-facebook);
    }

    .network-card__logo--instagram {
      background-color: var(--color-instagram);
    }

    .network-card__text {
      flex: 1 1 auto;
      min-inline-size: 0;
    }

    .network-card__name {
      overflow-wrap: anywhere;
      font-weight: 600;
    }

    .network-card__status {
      color: var(--color-text-muted);
      font-size: 0.8125rem;
    }

    .network-card__status--attention {
      color: var(--color-primary-hover);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.network-card--attention]': 'attention()',
    '[class.network-card--disabled]': 'disabled()',
    '[attr.aria-labelledby]': 'nameId()',
    role: 'group',
  },
})
export class NetworkCard {
  readonly network = input.required<'facebook' | 'instagram'>();
  readonly name = input.required<string>();
  readonly status = input.required<string>();
  readonly attention = input(false);
  readonly disabled = input(false);

  protected readonly nameId = computed(() => `network-card-${this.network()}`);
}
