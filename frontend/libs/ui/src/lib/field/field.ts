import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  ViewEncapsulation,
  booleanAttribute,
  computed,
  inject,
  input,
} from '@angular/core';
import { LucideCircleAlert } from '@lucide/angular';

let nextFieldId = 0;

/**
 * Field wrapper for a native `input`, `textarea` or `select` marked with `uiControl`:
 *
 * ```html
 * <ui-field label="Titre" hint="10 caractères minimum" [error]="titleError()" required>
 *   <input uiControl type="text" />
 * </ui-field>
 * ```
 *
 * The field links the label to the control and points `aria-describedby` to the hint and the
 * error; `aria-invalid` is set while an error is shown. All texts come from inputs.
 */
@Component({
  selector: 'ui-field',
  imports: [LucideCircleAlert],
  templateUrl: './field.html',
  styleUrl: './field.css',
  // The projected control must be styled: selectors are scoped by the .ui-field class instead.
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ui-field',
    '[class.ui-field--invalid]': 'invalid()',
  },
})
export class UiField {
  readonly label = input.required<string>();
  readonly hint = input<string>();
  readonly error = input<string | null>();
  readonly required = input(false, { transform: booleanAttribute });
  /** Id of the control; generated when not set. */
  readonly controlId = input(`ui-field-${nextFieldId++}`);

  readonly hintId = computed(() => `${this.controlId()}-hint`);
  readonly errorId = computed(() => `${this.controlId()}-error`);
  readonly invalid = computed(() => !!this.error());
  readonly describedBy = computed(() => {
    const ids = [this.hint() ? this.hintId() : null, this.invalid() ? this.errorId() : null];
    return ids.filter((id) => id !== null).join(' ') || null;
  });
}

/** Marks the native control of a `ui-field` and receives its id and ARIA attributes. */
@Directive({
  selector: 'input[uiControl], textarea[uiControl], select[uiControl]',
  host: {
    class: 'ui-control',
    '[id]': 'field.controlId()',
    '[attr.aria-describedby]': 'field.describedBy()',
    '[attr.aria-invalid]': "field.invalid() ? 'true' : null",
    '[required]': 'field.required()',
  },
})
export class UiFieldControl {
  protected readonly field = inject(UiField);
}
