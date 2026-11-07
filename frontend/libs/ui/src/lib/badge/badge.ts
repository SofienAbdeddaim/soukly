import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type UiBadgeTone = 'success' | 'warning' | 'danger' | 'pending' | 'neutral';

/**
 * Short status label in a pill, for example the state of a listing on "My listings" (ANN-05) or
 * where it was posted (SOC-06, `pending` while a post is being published):
 *
 * ```html
 * <ui-badge tone="warning">Expire dans 2 j</ui-badge>
 * ```
 *
 * The tone only changes the background: the text keeps the main text colour, so the meaning is
 * never carried by the colour alone.
 */
@Component({
  selector: 'ui-badge',
  template: '<ng-content />',
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      padding-block: 2px;
      padding-inline: 8px;
      border-radius: var(--radius-full);
      background-color: var(--color-surface-muted);
      color: var(--color-text);
      font-family: var(--font-body);
      font-size: 0.75rem;
      font-weight: 500;
      line-height: 1.4;
      white-space: nowrap;
    }

    :host(.ui-badge--success) {
      background-color: var(--color-success-surface);
    }

    :host(.ui-badge--warning) {
      background-color: var(--color-warning-surface);
    }

    :host(.ui-badge--danger) {
      background-color: var(--color-danger-surface);
    }

    :host(.ui-badge--pending) {
      background-color: var(--color-pending-surface);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': "'ui-badge ui-badge--' + tone()" },
})
export class UiBadge {
  readonly tone = input<UiBadgeTone>('neutral');
}
