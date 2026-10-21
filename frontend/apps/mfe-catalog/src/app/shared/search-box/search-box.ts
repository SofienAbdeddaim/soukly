import { ChangeDetectionStrategy, Component, input, linkedSignal, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideSearch } from '@lucide/angular';
import { MAX_QUERY_LENGTH } from '../../search/search-criteria';

let nextSearchBoxId = 0;

/**
 * Search field of the catalogue, on the indigo of the header (mockups CATA-02). Emits the text on
 * submit; the page decides where it leads (`/search?q=…`, other criteria kept).
 */
@Component({
  selector: 'app-search-box',
  imports: [TranslocoPipe, LucideSearch],
  template: `
    <form
      role="search"
      class="search-box"
      [class.search-box--large]="size() === 'large'"
      (submit)="$event.preventDefault(); submit()"
    >
      <label class="ui-visually-hidden" [for]="inputId">{{
        'catalog.search.label' | transloco
      }}</label>
      <input
        class="search-box__input"
        type="search"
        enterkeyhint="search"
        autocomplete="off"
        [id]="inputId"
        [maxLength]="maxLength"
        [placeholder]="'catalog.search.placeholder' | transloco"
        [value]="text()"
        (input)="text.set($any($event.target).value)"
      />
      <button
        type="submit"
        class="search-box__submit"
        [attr.aria-label]="size() === 'large' ? null : ('catalog.search.submit' | transloco)"
      >
        <svg lucideSearch [size]="20" aria-hidden="true"></svg>
        @if (size() === 'large') {
          <span>{{ 'catalog.search.submit' | transloco }}</span>
        }
      </button>
    </form>
  `,
  styleUrl: './search-box.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchBox {
  /** Text of the current search, shown in the field. */
  readonly query = input<string | null>(null);
  readonly size = input<'compact' | 'large'>('compact');
  readonly searched = output<string | null>();

  protected readonly text = linkedSignal(() => this.query() ?? '');
  protected readonly inputId = `search-box-${nextSearchBoxId++}`;
  protected readonly maxLength = MAX_QUERY_LENGTH;

  protected submit(): void {
    this.searched.emit(this.text().trim() || null);
  }
}
