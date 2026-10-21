import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import { SearchApi, SearchCard } from '@soukly/listing-data';
import { UiButton } from '@soukly/ui';
import { catchError, map, of, Subject, switchMap } from 'rxjs';
import { GovernorateMemory } from '../search/governorate-memory';
import { criteriaQueryParams, EMPTY_CRITERIA } from '../search/search-criteria';
import { CatalogReferentials } from '../shared/catalog-referentials';
import { ListingList } from '../shared/listing-list/listing-list';
import { PageMeta } from '../shared/page-meta';
import { SearchBox } from '../shared/search-box/search-box';
import { injectIsDesktop } from '../shared/viewport';
import { FamilyLinks } from './family-links/family-links';
import { GovernoratePicker } from './governorate-picker/governorate-picker';

/** Listings shown under "Latest listings" (CATA-02 hypothesis). */
export const LATEST_COUNT = 20;

/**
 * Home page (`/{lang}/`): search, the 10 families, then the latest listings of all of Tunisia or
 * of the remembered governorate, which the pill changes (and remembers).
 */
@Component({
  selector: 'app-home-page',
  imports: [
    RouterLink,
    TranslocoPipe,
    UiButton,
    FamilyLinks,
    GovernoratePicker,
    ListingList,
    SearchBox,
  ],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
  providers: [PageMeta],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {
  private readonly router = inject(Router);
  private readonly api = inject(SearchApi);
  private readonly memory = inject(GovernorateMemory);
  protected readonly locale = inject(LocaleService);
  protected readonly referentials = inject(CatalogReferentials);
  protected readonly desktop = injectIsDesktop();

  protected readonly governorate = this.memory.governorate;
  protected readonly latest = signal<SearchCard[]>([]);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  private readonly requests = new Subject<string | null>();

  constructor() {
    inject(PageMeta).set('seo.home-title', 'seo.home-description');
    void this.referentials.loadBasics();
    this.requests
      .pipe(
        switchMap((governorate) =>
          this.api.search({ governorate, sort: 'recent', limit: LATEST_COUNT }).pipe(
            map((page) => page.items),
            catchError(() => of(null)),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((items) => {
        if (items) {
          this.latest.set(items);
          this.state.set('ready');
        } else {
          this.state.set('error');
        }
      });
    effect(() => this.load(this.governorate()));
  }

  /** Query parameters of a new search: the remembered governorate. */
  protected newSearchParams(): Record<string, string> {
    const governorate = this.governorate();
    return governorate ? { governorate } : {};
  }

  protected search(q: string | null): void {
    void this.router.navigate(['/', this.locale.lang(), 'search'], {
      queryParams: criteriaQueryParams({ ...EMPTY_CRITERIA, q, governorate: this.governorate() }),
    });
  }

  protected chooseGovernorate(code: string | null): void {
    this.memory.remember(code);
  }

  protected retry(): void {
    this.load(this.governorate());
  }

  private load(governorate: string | null): void {
    this.state.set('loading');
    this.requests.next(governorate);
  }
}
