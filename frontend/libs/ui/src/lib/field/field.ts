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
 * <ui-field label="Title" hint="At least 10 characters" [error]="titleError()" required>
 *   <input uiControl type="text" />
 * </ui-field>
 * ```
 *
 * The field links the label to the control and points `aria-describedby` to the hint and the
 * error; `aria-invalid` is set while an error is shown. All texts come from inputs.
 *
 * Optional addons: a prefix glued before the control, a unit glued after it, and an icon button
 * inside its end.
 *
 * ```html
 * <ui-field label="Phone" ltr>
 *   <span uiFieldPrefix>+216</span>
 *   <input uiControl type="tel" />
 * </ui-field>
 * <ui-field label="Price">
 *   <input uiControl type="text" inputmode="numeric" dir="ltr" />
 *   <span uiFieldUnit>DT</span>
 * </ui-field>
 * <ui-field label="Password">
 *   <input uiControl type="password" />
 *   <button uiFieldSuffix type="button" aria-label="Show the password">…</button>
 * </ui-field>
 * ```
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
  /** Shown after the label of an optional field, for example "(optional)". */
  readonly optionalLabel = input<string | null>(null);
  /** Keeps the control and its addons left-to-right in RTL pages (phone numbers, emails). */
  readonly ltr = input(false, { transform: booleanAttribute });
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

/** Addon shown before the control of a `ui-field`, for example a fixed `+216`. */
@Directive({ selector: '[uiFieldPrefix]', host: { class: 'ui-field__prefix' } })
export class UiFieldPrefix {}

/** Text addon glued after the control of a `ui-field`: a unit or a currency (`DT`, `m²`). */
@Directive({ selector: '[uiFieldUnit]', host: { class: 'ui-field__unit' } })
export class UiFieldUnit {}

/** Icon button inside the end of the control of a `ui-field`; it needs an `aria-label`. */
@Directive({ selector: 'button[uiFieldSuffix]', host: { class: 'ui-field__suffix' } })
export class UiFieldSuffix {}
