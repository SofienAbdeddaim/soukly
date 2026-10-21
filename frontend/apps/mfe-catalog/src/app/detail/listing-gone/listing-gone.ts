import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideCalendarX2 } from '@lucide/angular';
import { LocaleService } from '@soukly/i18n';
import { SearchApi, SearchCard } from '@soukly/listing-data';
import { UiButton } from '@soukly/ui';
import { firstValueFrom } from 'rxjs';
import {
  criteriaCommands,
  criteriaQueryParams,
  EMPTY_CRITERIA,
} from '../../search/search-criteria';
import { ListingList } from '../../shared/listing-list/listing-list';
import { injectIsDesktop } from '../../shared/viewport';
import { ListingPageViews } from '../listing-page-view';

/** Similar listings shown under a listing that is no longer available. */
export const SIMILAR_COUNT = 4;

/**
 * "This listing is no longer available" (CATA-03, `410`): a link to the listings of the same
 * subcategory in the same governorate, then the 4 most recent of them (CATA-01); when there are
 * fewer, the most recent of the subcategory in the whole of Tunisia complete them.
 */
@Component({
  selector: 'app-listing-gone',
  imports: [RouterLink, TranslocoPipe, LucideCalendarX2, UiButton, ListingList],
  template: `
    <div class="gone__state" data-testid="listing-gone">
      <span class="gone__icon" aria-hidden="true"
        ><svg lucideCalendarX2 [size]="34" [strokeWidth]="1.8"></svg
      ></span>
      <h1 class="gone__title">{{ 'catalog.gone.title' | transloco }}</h1>
      <p class="gone__text">{{ 'catalog.gone.text' | transloco }}</p>
      @if (seeAllKey(); as key) {
        <a
          ui-button
          size="lg"
          data-testid="gone-see-all"
          [routerLink]="seeAll().commands"
          [queryParams]="seeAll().queryParams"
        >
          {{ key | transloco: { category: subcategory(), place: governorate() } }}
        </a>
      }
    </div>
    @if (loading() || similar().length) {
      <h2 class="gone__similar">{{ 'catalog.gone.similar' | transloco }}</h2>
      <app-listing-list
        [items]="similar()"
        [loading]="loading()"
        [skeletonCount]="similarCount"
        [grid]="desktop()"
        [label]="'catalog.gone.similar' | transloco"
      />
    }
  `,
  styleUrl: './listing-gone.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListingGone {
  private readonly search = inject(SearchApi);
  private readonly locale = inject(LocaleService);
  private readonly views = inject(ListingPageViews);
  protected readonly desktop = injectIsDesktop();

  readonly categoryCode = input<string | null>(null);
  readonly governorateCode = input<string | null>(null);

  protected readonly similarCount = SIMILAR_COUNT;
  protected readonly similar = signal<SearchCard[]>([]);
  protected readonly loading = signal(false);

  protected readonly subcategory = computed(() => this.views.subcategoryLabel(this.categoryCode()));
  protected readonly governorate = computed(() =>
    this.views.governorateLabel(this.governorateCode()),
  );

  protected readonly seeAllKey = computed(() => {
    if (!this.categoryCode() || !this.subcategory()) {
      return null;
    }
    return this.governorate() ? 'catalog.gone.see-all-place' : 'catalog.gone.see-all';
  });

  protected readonly seeAll = computed(() => {
    const criteria = {
      ...EMPTY_CRITERIA,
      category: this.categoryCode(),
      governorate: this.governorateCode(),
    };
    return {
      commands: criteriaCommands(this.locale.lang(), criteria),
      queryParams: criteriaQueryParams(criteria),
    };
  });

  constructor() {
    effect(() => {
      const category = this.categoryCode();
      const governorate = this.governorateCode();
      void this.loadSimilar(category, governorate);
    });
  }

  private async loadSimilar(category: string | null, governorate: string | null): Promise<void> {
    if (!category) {
      this.similar.set([]);
      return;
    }
    this.loading.set(true);
    try {
      let cards = governorate ? await this.page(category, governorate) : [];
      if (cards.length < SIMILAR_COUNT) {
        const known = new Set(cards.map((card) => card.id));
        const everywhere = await this.page(category, null);
        cards = [...cards, ...everywhere.filter((card) => !known.has(card.id))].slice(
          0,
          SIMILAR_COUNT,
        );
      }
      this.similar.set(cards);
    } catch {
      // Only a suggestion: the page stays useful without it
      this.similar.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private async page(category: string, governorate: string | null): Promise<SearchCard[]> {
    const page = await firstValueFrom(
      this.search.search({ category, governorate, limit: SIMILAR_COUNT }),
    );
    return page.items;
  }
}
