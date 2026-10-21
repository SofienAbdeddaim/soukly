import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  ViewEncapsulation,
  computed,
  inject,
  input,
} from '@angular/core';
import { LucideCircleAlert } from '@lucide/angular';

let nextCheckboxId = 0;

/**
 * Checkbox with its label, which can contain links, and an optional error:
 *
 * ```html
 * <ui-checkbox [error]="termsError()">
 *   <input uiCheckboxControl type="checkbox" />
 *   I accept the <a href="/terms">terms of use</a>
 * </ui-checkbox>
 * ```
 *
 * The label is linked to the control; `aria-describedby` and `aria-invalid` follow the error.
 */
@Component({
  selector: 'ui-checkbox',
  imports: [LucideCircleAlert],
  template: `
    <div class="ui-checkbox__row">
      <ng-content select="input" />
      <label class="ui-checkbox__label" [for]="controlId()"><ng-content /></label>
    </div>
    @if (invalid()) {
      <p class="ui-checkbox__error" [id]="errorId()">
        <svg lucideCircleAlert [size]="16" aria-hidden="true"></svg>
        <span>{{ error() }}</span>
      </p>
    }
  `,
  styleUrl: './checkbox.css',
  // The projected input must be styled: selectors are scoped by the .ui-checkbox class instead.
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ui-checkbox',
    '[class.ui-checkbox--invalid]': 'invalid()',
  },
})
export class UiCheckbox {
  readonly error = input<string | null>();
  /** Id of the control; generated when not set. */
  readonly controlId = input(`ui-checkbox-${nextCheckboxId++}`);

  readonly errorId = computed(() => `${this.controlId()}-error`);
  readonly invalid = computed(() => !!this.error());
}

/** Marks the native checkbox of a `ui-checkbox` and receives its id and ARIA attributes. */
@Directive({
  selector: 'input[type=checkbox][uiCheckboxControl]',
  host: {
    class: 'ui-checkbox__control',
    '[id]': 'checkbox.controlId()',
    '[attr.aria-describedby]': 'checkbox.invalid() ? checkbox.errorId() : null',
    '[attr.aria-invalid]': "checkbox.invalid() ? 'true' : null",
  },
})
export class UiCheckboxControl {
  protected readonly checkbox = inject(UiCheckbox);
}
