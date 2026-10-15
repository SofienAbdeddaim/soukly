import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  booleanAttribute,
  inject,
  input,
} from '@angular/core';

export type UiButtonVariant = 'primary' | 'secondary' | 'ghost';
export type UiButtonSize = 'md' | 'lg';

/**
 * Button styles for a native `button` or `a`: the element keeps its native semantics.
 * An optional icon is projected before the label: `<svg lucidePlus uiButtonIcon></svg>`.
 * While `loading` is true the element stays focusable, is marked `aria-busy` and ignores clicks.
 */
@Component({
  selector: 'button[ui-button], a[ui-button]',
  templateUrl: './button.html',
  styleUrl: './button.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ui-button',
    '[class.ui-button--primary]': "variant() === 'primary'",
    '[class.ui-button--secondary]': "variant() === 'secondary'",
    '[class.ui-button--ghost]': "variant() === 'ghost'",
    '[class.ui-button--lg]': "size() === 'lg'",
    '[class.ui-button--loading]': 'loading()',
    '[attr.disabled]': "isButton && disabled() ? '' : null",
    '[attr.aria-disabled]': "(!isButton && disabled()) || loading() ? 'true' : null",
    '[attr.aria-busy]': "loading() ? 'true' : null",
    '[attr.tabindex]': "!isButton && disabled() ? '-1' : null",
  },
})
export class UiButton {
  readonly variant = input<UiButtonVariant>('primary');
  readonly size = input<UiButtonSize>('md');
  readonly loading = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  protected readonly isButton = this.element.tagName === 'BUTTON';

  constructor() {
    // Capture phase: runs before the (click) listeners of the consumer on the same element.
    const blockClick = (event: Event) => {
      if (this.loading() || (!this.isButton && this.disabled())) {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    };
    this.element.addEventListener('click', blockClick, { capture: true });
    inject(DestroyRef).onDestroy(() =>
      this.element.removeEventListener('click', blockClick, { capture: true }),
    );
  }
}
