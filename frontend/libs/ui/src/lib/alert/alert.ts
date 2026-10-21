import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  booleanAttribute,
  inject,
  input,
} from '@angular/core';
import { LucideCircleAlert, LucideCircleCheck, LucideInfo } from '@lucide/angular';

export type UiAlertType = 'info' | 'success' | 'error';

/**
 * Message about a whole form or page, for example "Incorrect email or password" above a form:
 *
 * ```html
 * <ui-alert type="error" #alert>{{ 'login.invalid-credentials' | transloco }}</ui-alert>
 * ```
 *
 * `error` alerts are `role="alert"` (announced at once), the others `role="status"`. The alert can
 * receive the focus (`tabindex="-1"`): call `focus()` after showing it, so keyboard and screen
 * reader users land on it. Set `live` to false for a message present when the page opens, which
 * must not be announced on its own.
 */
@Component({
  selector: 'ui-alert',
  imports: [LucideCircleAlert, LucideCircleCheck, LucideInfo],
  template: `
    <span class="ui-alert__icon" aria-hidden="true">
      @switch (type()) {
        @case ('error') {
          <svg lucideCircleAlert [size]="20"></svg>
        }
        @case ('success') {
          <svg lucideCircleCheck [size]="20"></svg>
        }
        @default {
          <svg lucideInfo [size]="20"></svg>
        }
      }
    </span>
    <div class="ui-alert__content"><ng-content /></div>
  `,
  styleUrl: './alert.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    tabindex: '-1',
    '[class]': "'ui-alert ui-alert--' + type()",
    '[attr.role]': "live() ? (type() === 'error' ? 'alert' : 'status') : null",
  },
})
export class UiAlert {
  readonly type = input<UiAlertType>('info');
  readonly live = input(true, { transform: booleanAttribute });

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  focus(): void {
    this.element.focus();
  }
}
