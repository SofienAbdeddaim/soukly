import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
  ViewEncapsulation,
} from '@angular/core';

let nextSwitchId = 0;

/**
 * On/off switch with its label, for a setting applied at once:
 *
 * ```html
 * <ui-switch [checked]="autoShare()" (checkedChange)="save($event)">
 *   Tick by default for every new listing
 * </ui-switch>
 * ```
 *
 * Controlled: a click asks for the other value (`checkedChange`) and the switch shows `checked`,
 * so the owner can go back when the saving fails.
 * A native button with `role="switch"` and `aria-checked`, named by the projected label; Space
 * and Enter toggle it. The thumb moves with the reading direction (logical properties), and the
 * touch target is 44px high.
 */
@Component({
  selector: 'ui-switch',
  template: `
    <span class="ui-switch__label" [id]="labelId"><ng-content /></span>
    <button
      type="button"
      role="switch"
      class="ui-switch__control"
      [attr.aria-checked]="checked() ? 'true' : 'false'"
      [attr.aria-labelledby]="labelId"
      [attr.aria-busy]="busy() ? 'true' : null"
      [disabled]="disabled()"
      (click)="toggle()"
    >
      <span class="ui-switch__track" aria-hidden="true">
        <span class="ui-switch__thumb"></span>
      </span>
    </button>
  `,
  styleUrl: './switch.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ui-switch',
    '[class.ui-switch--checked]': 'checked()',
    '[class.ui-switch--disabled]': 'disabled()',
  },
})
export class UiSwitch {
  readonly checked = input(false);
  readonly checkedChange = output<boolean>();
  readonly disabled = input(false);
  /** The change is being saved: the switch keeps its new position and says it is busy. */
  readonly busy = input(false);

  protected readonly labelId = `ui-switch-${nextSwitchId++}-label`;

  protected toggle(): void {
    if (!this.disabled()) {
      this.checkedChange.emit(!this.checked());
    }
  }
}
