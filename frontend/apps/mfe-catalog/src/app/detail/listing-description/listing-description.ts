import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Description of a listing (CATA-03): plain text with its line breaks, never read as HTML
 * (`textContent` only), folded beyond 6 lines with a "Read more" button that unfolds it.
 */
@Component({
  selector: 'app-listing-description',
  imports: [TranslocoPipe],
  template: `
    <p
      #paragraph
      id="listing-description-text"
      class="description"
      [class.description--folded]="!expanded()"
      dir="auto"
      data-testid="description"
      [textContent]="text()"
    ></p>
    @if (overflows() || expanded()) {
      <button
        type="button"
        class="description__toggle"
        aria-controls="listing-description-text"
        [attr.aria-expanded]="expanded()"
        (click)="expanded.set(!expanded())"
      >
        {{ (expanded() ? 'catalog.detail.read-less' : 'catalog.detail.read-more') | transloco }}
      </button>
    }
  `,
  styleUrl: './listing-description.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListingDescription {
  readonly text = input.required<string>();

  protected readonly expanded = signal(false);
  protected readonly overflows = signal(false);
  private readonly paragraph = viewChild.required<ElementRef<HTMLElement>>('paragraph');

  constructor() {
    afterRenderEffect(() => {
      this.text();
      if (!this.expanded()) {
        const element = this.paragraph().nativeElement;
        this.overflows.set(element.scrollHeight > element.clientHeight + 1);
      }
    });
  }
}
