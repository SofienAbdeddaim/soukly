import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideX } from '@lucide/angular';
import { formatNumber, LocaleService } from '@soukly/i18n';
import { UiButton } from '@soukly/ui';
import { debounceTime, Observable, skip, switchMap, tap } from 'rxjs';
import { SearchCriteria, withoutFilters } from '../search/search-criteria';
import { pluralKey } from '../shared/plural';
import { FiltersForm } from './filters-form';

/** Delay after the last change before the number of listings is asked again (CATA-02). */
export const COUNT_DEBOUNCE_MS = 400;

export interface FiltersDialogData {
  criteria: SearchCriteria;
  /** Number of listings of `criteria`, known by the page. */
  total: number | null;
  /** Number of listings of other criteria (`limit=1` request). */
  count: (criteria: SearchCriteria) => Observable<number | null>;
}

/**
 * Filters of the results on mobile: a full-screen modal dialog (CDK, focus trapped then given back
 * to the "Filters" button). The changes stay in the dialog until "Show n listings", whose number
 * is asked again 400 ms after each change; closing the dialog drops them. Closes with the
 * criteria to apply, or nothing.
 */
@Component({
  selector: 'app-filters-dialog',
  imports: [TranslocoPipe, LucideX, UiButton, FiltersForm],
  template: `
    <header class="dialog__header">
      <h2 id="filters-dialog-title" class="dialog__title">
        {{ 'catalog.filters.title' | transloco }}
      </h2>
      <button type="button" class="dialog__clear" (click)="clear()">
        {{ 'catalog.filters.clear' | transloco }}
      </button>
      <button
        type="button"
        class="dialog__close"
        [attr.aria-label]="'catalog.actions.close' | transloco"
        (click)="dialogRef.close()"
      >
        <svg lucideX [size]="22" aria-hidden="true"></svg>
      </button>
    </header>
    <div class="dialog__body">
      <app-filters-form
        idPrefix="dialog"
        [criteria]="draft()"
        (criteriaChange)="draft.set($event)"
      />
    </div>
    <footer class="dialog__footer">
      <button
        ui-button
        type="button"
        size="lg"
        class="dialog__apply"
        data-testid="apply-filters"
        (click)="apply()"
      >
        @if (count(); as count) {
          {{ countKey(count) | transloco: { count: countText(count) } }}
        } @else if (count() === 0) {
          {{ 'catalog.filters.show-none' | transloco }}
        } @else {
          {{ 'catalog.filters.show-unknown' | transloco }}
        }
      </button>
    </footer>
  `,
  styleUrl: './filters-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'filters-dialog' },
})
export class FiltersDialog {
  protected readonly dialogRef = inject<DialogRef<SearchCriteria>>(DialogRef);
  private readonly data = inject<FiltersDialogData>(DIALOG_DATA);
  private readonly locale = inject(LocaleService);

  protected readonly draft = signal(this.data.criteria);
  protected readonly count = signal<number | null>(this.data.total);

  constructor() {
    toObservable(this.draft)
      .pipe(
        // The number of the criteria the dialog opens with is already known.
        skip(1),
        debounceTime(COUNT_DEBOUNCE_MS),
        tap(() => this.count.set(null)),
        switchMap((criteria) => this.data.count(criteria)),
        takeUntilDestroyed(),
      )
      .subscribe((count) => this.count.set(count));
  }

  protected countKey(count: number): string {
    return pluralKey('catalog.filters.show', count, this.locale.intlLocale());
  }

  protected countText(count: number): string {
    return formatNumber(count, this.locale.intlLocale());
  }

  protected clear(): void {
    this.draft.set(withoutFilters(this.draft()));
  }

  protected apply(): void {
    this.dialogRef.close(this.draft());
  }
}
