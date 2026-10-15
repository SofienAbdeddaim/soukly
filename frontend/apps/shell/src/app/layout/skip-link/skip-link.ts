import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * "Skip to content" link, first focusable element of the page, visible only when focused.
 * It moves the focus itself: a plain "#main-content" link would be resolved against <base href>
 * and leave the current route.
 */
@Component({
  selector: 'app-skip-link',
  imports: [TranslocoPipe],
  template: `<a class="skip-link" [href]="'#' + targetId()" (click)="skip($event)">{{
    'skip-link' | transloco
  }}</a>`,
  styles: `
    .skip-link {
      position: absolute;
      inset-inline-start: 1rem;
      inset-block-start: -100px;
      z-index: 100;
      padding-block: 0.75rem;
      padding-inline: 1rem;
      border-radius: var(--radius-md);
      background-color: var(--color-surface);
      color: var(--color-text);
      font-weight: 600;
    }

    .skip-link:focus {
      inset-block-start: 1rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkipLink {
  readonly targetId = input('main-content');

  private readonly document = inject(DOCUMENT);

  protected skip(event: Event): void {
    event.preventDefault();
    this.document.getElementById(this.targetId())?.focus();
  }
}
