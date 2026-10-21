import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type UiProgressTone = 'default' | 'on-brand';

/**
 * Thin progress bar, for example the steps of a form or the upload of a photo:
 *
 * ```html
 * <ui-progress [value]="40" label="Progress" valueText="Step 2 of 5: Photos" tone="on-brand" />
 * ```
 *
 * `role="progressbar"` with its value (0 to 100) and an optional text read instead of the
 * percentage. `on-brand` is the variant for an indigo background.
 */
@Component({
  selector: 'ui-progress',
  template: `<span class="ui-progress__bar" [style.inline-size.%]="clamped()"></span>`,
  styles: `
    :host {
      display: block;
      overflow: hidden;
      block-size: 6px;
      border-radius: var(--radius-full);
      background-color: var(--color-surface-muted);
    }

    :host(.ui-progress--on-brand) {
      background-color: var(--color-brand-track);
    }

    .ui-progress__bar {
      display: block;
      block-size: 100%;
      border-radius: inherit;
      background-color: var(--color-primary);
      transition: inline-size 0.2s ease-out;
    }

    :host(.ui-progress--on-brand) .ui-progress__bar {
      background-color: var(--color-accent);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'progressbar',
    'aria-valuemin': '0',
    'aria-valuemax': '100',
    '[attr.aria-valuenow]': 'clamped()',
    '[attr.aria-valuetext]': 'valueText() ?? null',
    '[attr.aria-label]': 'label()',
    '[class.ui-progress--on-brand]': "tone() === 'on-brand'",
  },
})
export class UiProgress {
  /** From 0 to 100. */
  readonly value = input.required<number>();
  /** Accessible name of the bar. */
  readonly label = input.required<string>();
  readonly valueText = input<string>();
  readonly tone = input<UiProgressTone>('default');

  protected readonly clamped = computed(() => Math.round(Math.min(100, Math.max(0, this.value()))));
}
