import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  input,
  output,
} from '@angular/core';
import { LucideCircleAlert } from '@lucide/angular';

let nextToggleGroupId = 0;

export interface UiToggleOption {
  value: string;
  label: string;
}

export type UiToggleGroupLayout = 'pills' | 'tiles';

/**
 * Single choice (or, with `multiple`, several choices) among a few options, shown as toggle
 * buttons in a `fieldset`:
 *
 * ```html
 * <ui-toggle-group legend="Fuel" [options]="fuels" [value]="fuel()" (valueChange)="fuel.set($event)" />
 * ```
 *
 * Each option is a `button` with `aria-pressed`. `pills` wrap on one line (enumerations);
 * `tiles` are a grid of two columns (category families). A pressed option is unselected by a
 * second press unless `required` is set (`valueChange` then emits null). With `multiple`, each press
 * adds or removes its option: the group reads `values` and emits `valuesChange` instead, in the order
 * of the options (search filters, CATA-02). All texts come from inputs.
 */
@Component({
  selector: 'ui-toggle-group',
  imports: [LucideCircleAlert],
  template: `
    <fieldset
      class="ui-toggle-group__fieldset"
      [attr.aria-describedby]="invalid() ? errorId : null"
    >
      <legend class="ui-toggle-group__legend" [class.ui-visually-hidden]="hideLegend()">
        {{ legend() }}
        @if (optionalLabel(); as optional) {
          <span class="ui-toggle-group__optional">{{ optional }}</span>
        }
      </legend>
      <div
        class="ui-toggle-group__options"
        [class.ui-toggle-group__options--tiles]="layout() === 'tiles'"
      >
        @for (option of options(); track option.value) {
          <button
            type="button"
            class="ui-toggle-group__option"
            [class.ui-toggle-group__option--tile]="layout() === 'tiles'"
            [attr.aria-pressed]="isPressed(option.value)"
            [disabled]="disabled()"
            (click)="press(option.value)"
          >
            {{ option.label }}
          </button>
        }
      </div>
      @if (invalid()) {
        <p class="ui-toggle-group__error" [id]="errorId">
          <svg lucideCircleAlert [size]="16" aria-hidden="true"></svg>
          <span>{{ error() }}</span>
        </p>
      }
    </fieldset>
  `,
  styleUrl: './toggle-group.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UiToggleGroup {
  readonly legend = input.required<string>();
  readonly options = input.required<readonly UiToggleOption[]>();
  readonly value = input<string | null>(null);
  readonly multiple = input(false, { transform: booleanAttribute });
  /** Pressed options of a `multiple` group. */
  readonly values = input<readonly string[]>([]);
  readonly layout = input<UiToggleGroupLayout>('pills');
  readonly required = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Shown after the legend of an optional choice, for example "(optional)". */
  readonly optionalLabel = input<string | null>(null);
  /** Keeps the legend for screen readers only, when a heading already names the choice. */
  readonly hideLegend = input(false, { transform: booleanAttribute });
  readonly error = input<string | null>();

  readonly valueChange = output<string | null>();
  readonly valuesChange = output<string[]>();

  protected readonly errorId = `ui-toggle-group-${nextToggleGroupId++}-error`;
  protected readonly invalid = computed(() => !!this.error());

  protected isPressed(value: string): boolean {
    return this.multiple() ? this.values().includes(value) : value === this.value();
  }

  protected press(value: string): void {
    if (this.multiple()) {
      const pressed = new Set(this.values());
      if (pressed.has(value)) {
        pressed.delete(value);
      } else {
        pressed.add(value);
      }
      this.valuesChange.emit(
        this.options()
          .map((option) => option.value)
          .filter((option) => pressed.has(option)),
      );
      return;
    }
    if (value !== this.value()) {
      this.valueChange.emit(value);
    } else if (!this.required()) {
      this.valueChange.emit(null);
    }
  }
}
