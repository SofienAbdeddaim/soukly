import { Dialog } from '@angular/cdk/dialog';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  DOCUMENT,
  effect,
  inject,
  Injector,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideSearchX, LucideSlidersHorizontal } from '@lucide/angular';
import { formatNumber, LocaleService } from '@soukly/i18n';
import { labelOf, SEARCH_SORTS, SearchSort } from '@soukly/listing-data';
import { UiAlert, UiButton } from '@soukly/ui';
import { combineLatest, map } from 'rxjs';
import { FiltersDialog, FiltersDialogData } from '../filters/filters-dialog';
import { FiltersForm } from '../filters/filters-form';
import { GovernorateMemory } from '../search/governorate-memory';
import {
  ActiveFilter,
  activeFilters,
  criteriaCommands,
  criteriaFromUrl,
  criteriaQueryParams,
  effectiveSort,
  isSubcategory,
  sameCriteria,
  SearchCriteria,
  withGovernorate,
  withoutFilter,
  withoutFilters,
  withQuery,
} from '../search/search-criteria';
import { SearchSnapshots } from '../search/search-snapshots';
import { SearchStore } from '../search/search-store';
import { CatalogReferentials } from '../shared/catalog-referentials';
import { ListingList } from '../shared/listing-list/listing-list';
import { NearEnd } from '../shared/near-end';
import { PageMeta } from '../shared/page-meta';
import { pluralKey } from '../shared/plural';
import { SearchBox } from '../shared/search-box/search-box';
import { injectIsDesktop } from '../shared/viewport';
import { FilterChips } from './filter-chips/filter-chips';

/** A suggestion of the "no result" state: a key of the `catalog` scope and the search it leads to. */
interface Suggestion {
  key: string;
  params: Record<string, string>;
  criteria: SearchCriteria;
}

/**
 * Results page (`/{lang}/search`, `/{lang}/c/{category}`): the criteria are read from the URL only
 * and every change navigates, so a shared link gives the same search and the back button undoes
 * the last change. Filters in a column on desktop, in a dialog on mobile; the next 24 results
 * load near the bottom of the list, with a "Show more" button as a fallback. Back from a
 * listing, the page comes back as it was left (results and scroll position).
 */
@Component({
  selector: 'app-results-page',
  imports: [
    RouterLink,
    TranslocoPipe,
    LucideSearchX,
    LucideSlidersHorizontal,
    UiAlert,
    UiButton,
    FilterChips,
    FiltersForm,
    ListingList,
    NearEnd,
    SearchBox,
  ],
  templateUrl: './results-page.html',
  styleUrl: './results-page.css',
  providers: [SearchStore, PageMeta],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(Dialog);
  private readonly injector = inject(Injector);
  private readonly window = inject(DOCUMENT).defaultView;
  private readonly memory = inject(GovernorateMemory);
  private readonly snapshots = inject(SearchSnapshots);
  private readonly pageMeta = inject(PageMeta);
  protected readonly locale = inject(LocaleService);
  protected readonly referentials = inject(CatalogReferentials);
  protected readonly store = inject(SearchStore);
  protected readonly desktop = injectIsDesktop();

  protected readonly sorts = SEARCH_SORTS;

  /** The criteria of the URL: the only state of the search. */
  protected readonly criteria = toSignal(
    combineLatest([this.route.paramMap, this.route.queryParams]).pipe(
      map(([params, query]) => criteriaFromUrl(params.get('categoryCode'), query)),
    ),
    { requireSync: true },
  );

  protected readonly sort = computed(() => effectiveSort(this.criteria()));

  protected readonly filters = computed(() => {
    const category = this.criteria().category;
    const types: Record<string, 'ENUM' | 'INTEGER' | 'BOOLEAN' | 'TEXT'> = {};
    if (isSubcategory(category)) {
      for (const attribute of this.referentials.attributes()[category] ?? []) {
        types[attribute.code] = attribute.type;
      }
    }
    return activeFilters(this.criteria(), types);
  });

  protected readonly categoryLabel = computed(() => {
    const code = this.criteria().category;
    if (!code) {
      return null;
    }
    const labels =
      this.referentials.subcategories().get(code)?.label ??
      this.referentials.families().find((family) => family.code === code)?.label;
    return labels ? labelOf(labels, this.locale.lang()) : null;
  });

  protected readonly placeLabel = computed(() => {
    const { governorate, delegation } = this.criteria();
    const lang = this.locale.lang();
    const found = this.referentials.governorates().find((item) => item.code === governorate);
    if (!found) {
      return null;
    }
    const part = delegation
      ? this.referentials.delegations()[found.code]?.find((item) => item.code === delegation)
      : undefined;
    return part
      ? `${labelOf(found.label, lang)}, ${labelOf(part.label, lang)}`
      : labelOf(found.label, lang);
  });

  /** Key of "48 listings", with its plural form. */
  protected readonly countKey = computed(() =>
    pluralKey('catalog.results.count', this.store.total() ?? 0, this.locale.intlLocale()),
  );
  protected readonly countText = computed(() =>
    formatNumber(this.store.total() ?? 0, this.locale.intlLocale()),
  );

  protected readonly whereKey = computed(() => {
    const category = this.categoryLabel();
    const place = this.placeLabel();
    if (category && place) {
      return 'catalog.results.in-category-place';
    }
    if (category) {
      return 'catalog.results.in-category';
    }
    return place ? 'catalog.results.in-place' : 'catalog.results.everywhere';
  });

  protected readonly suggestions = computed<Suggestion[]>(() => {
    const criteria = this.criteria();
    const suggestions: Suggestion[] = [];
    const price = this.filters().find((filter) => filter.kind === 'price');
    if (price) {
      suggestions.push({
        key: 'catalog.empty.remove-price',
        params: {},
        criteria: withoutFilter(criteria, price),
      });
    }
    if (criteria.governorate) {
      suggestions.push({
        key: 'catalog.empty.everywhere',
        params: {},
        criteria: withGovernorate(criteria, null),
      });
    }
    if (Object.keys(criteria.attributes).length) {
      suggestions.push({
        key: 'catalog.empty.remove-specific',
        params: {},
        criteria: { ...criteria, attributes: {} },
      });
    }
    if (criteria.q) {
      suggestions.push({
        key: 'catalog.empty.remove-query',
        params: { q: criteria.q },
        criteria: withQuery(criteria, null),
      });
    }
    return suggestions;
  });

  private scrollY = 0;

  constructor() {
    void this.referentials.loadBasics();
    this.restoreOrRedirect();

    // Every later change of the URL (filters, chips, sort, back button) is a new search.
    effect(() => {
      const criteria = this.criteria();
      untracked(() => {
        const first = this.store.status() === 'idle';
        if (first && this.redirects(criteria)) {
          return;
        }
        if (first || !sameCriteria(criteria, this.store.criteria())) {
          this.store.search(criteria);
        }
      });
    });
    effect(() => {
      const criteria = this.criteria();
      if (isSubcategory(criteria.category)) {
        void this.referentials.loadAttributes(criteria.category);
      }
      if (criteria.governorate) {
        void this.referentials.loadDelegations(criteria.governorate);
      }
    });
    effect(() => this.updateMeta());

    const onScroll = () => (this.scrollY = this.window?.scrollY ?? 0);
    this.window?.addEventListener('scroll', onScroll, { passive: true });
    inject(DestroyRef).onDestroy(() => {
      this.window?.removeEventListener('scroll', onScroll);
      if (this.store.status() === 'ready') {
        this.snapshots.keep(this.urlOf(this.store.criteria()), {
          snapshot: this.store.snapshot(),
          scrollY: this.scrollY,
        });
      }
    });
  }

  /** Changes the URL; the search follows. The governorate chosen is remembered. */
  protected apply(criteria: SearchCriteria): void {
    if (criteria.governorate !== this.criteria().governorate) {
      this.memory.remember(criteria.governorate);
    }
    void this.router.navigate(criteriaCommands(this.locale.lang(), criteria), {
      queryParams: criteriaQueryParams(criteria),
    });
  }

  protected search(q: string | null): void {
    this.apply(withQuery(this.criteria(), q));
  }

  protected removeFilter(filter: ActiveFilter): void {
    this.apply(withoutFilter(this.criteria(), filter));
  }

  protected chooseSort(sort: string): void {
    const chosen = SEARCH_SORTS.includes(sort as SearchSort) ? (sort as SearchSort) : null;
    const byDefault = effectiveSort({ ...this.criteria(), sort: null });
    this.apply({ ...this.criteria(), sort: chosen === byDefault ? null : chosen });
  }

  protected openFilters(): void {
    const data: FiltersDialogData = {
      criteria: this.criteria(),
      total: this.store.total(),
      count: (criteria) => this.store.count(criteria),
    };
    const dialogRef = this.dialog.open<SearchCriteria, FiltersDialogData, FiltersDialog>(
      FiltersDialog,
      {
        data,
        injector: this.injector,
        ariaLabelledBy: 'filters-dialog-title',
        width: '100%',
        height: '100%',
        maxWidth: '100vw',
        maxHeight: '100dvh',
        panelClass: 'filters-dialog-panel',
      },
    );
    dialogRef.closed.subscribe((criteria) => {
      if (criteria && !sameCriteria(criteria, this.criteria())) {
        this.apply(criteria);
        this.window?.scrollTo({ top: 0 });
      }
    });
  }

  protected clearFilters(): void {
    this.apply(withoutFilters(this.criteria()));
  }

  protected newSearchCommands(): string[] {
    return ['/', this.locale.lang(), 'search'];
  }

  /**
   * Back from a listing, the results kept when the page was left come back with their scroll
   * position; a bare `/search` gets the remembered governorate (a new search).
   */
  private restoreOrRedirect(): void {
    const criteria = this.criteria();
    const navigation = this.router.currentNavigation();
    const kept =
      navigation?.trigger === 'popstate' ? this.snapshots.take(this.urlOf(criteria)) : null;
    if (kept && sameCriteria(kept.snapshot.criteria, criteria)) {
      this.store.restore(kept.snapshot);
      afterNextRender(() => this.window?.scrollTo({ top: kept.scrollY }), {
        injector: this.injector,
      });
      return;
    }
    if (this.redirects(criteria)) {
      void this.router.navigate(criteriaCommands(this.locale.lang(), criteria), {
        queryParams: { governorate: this.memory.governorate() },
        replaceUrl: true,
      });
    }
  }

  /** A bare `/search` while a governorate is remembered. */
  private redirects(criteria: SearchCriteria): boolean {
    const bare = !criteria.category && !Object.keys(criteriaQueryParams(criteria)).length;
    return bare && !!this.memory.governorate();
  }

  private urlOf(criteria: SearchCriteria): string {
    return this.router.serializeUrl(
      this.router.createUrlTree(criteriaCommands(this.locale.lang(), criteria), {
        queryParams: criteriaQueryParams(criteria),
      }),
    );
  }

  private updateMeta(): void {
    const what = this.categoryLabel() ?? this.criteria().q;
    const where = this.placeLabel();
    const variant = what ? (where ? 'what-where' : 'what') : where ? 'where' : 'all';
    this.pageMeta.set(`seo.results-title-${variant}`, 'seo.results-description', {
      what: what ?? '',
      where: where ?? '',
    });
  }
}
