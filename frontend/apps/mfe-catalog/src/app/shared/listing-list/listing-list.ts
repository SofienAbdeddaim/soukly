import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SearchCard } from '@soukly/listing-data';
import { UiListingCard, UiListingRow, UiSkeleton } from '@soukly/ui';
import { ListingViews } from '../listing-view';
import { SpaLinks } from '../spa-links';

/**
 * Listings of the catalogue as a semantic list (`ul` of `li` holding an `article`): horizontal rows
 * below 768 px, a grid of cards above (CATA-02). While `loading` with nothing to show yet, skeleton
 * cards take their place. No data of the seller is shown: the cards of the search have none.
 */
@Component({
  selector: 'app-listing-list',
  imports: [TranslocoPipe, UiListingCard, UiListingRow, UiSkeleton, SpaLinks],
  template: `
    @if (loading() && !items().length) {
      <ul
        class="listings"
        [class.listings--grid]="grid()"
        aria-hidden="true"
        data-testid="skeletons"
      >
        @for (skeleton of skeletons(); track $index) {
          <li>
            @if (grid()) {
              <ui-skeleton variant="card" />
            } @else {
              <ui-skeleton variant="block" blockSize="108px" />
            }
          </li>
        }
      </ul>
    } @else {
      <ul
        class="listings"
        [class.listings--grid]="grid()"
        [attr.aria-label]="label()"
        appSpaLinks
        data-testid="listings"
      >
        @for (view of views(); track view.id) {
          <li [attr.data-listing-id]="view.id">
            @let price =
              (view.price ?? ('catalog.card.on-quote' | transloco)) +
              (view.unitKey ? (view.unitKey | transloco) : '');
            @if (grid()) {
              <ui-listing-card
                [title]="view.title"
                [price]="price"
                [city]="view.place"
                [dateLabel]="view.dateLabel"
                [dateTime]="view.dateTime"
                [href]="view.href"
                [imageUrl]="view.imageUrl"
              />
            } @else {
              <ui-listing-row
                [title]="view.title"
                [price]="price"
                [summary]="view.summary"
                [place]="view.place"
                [dateLabel]="view.dateLabel"
                [dateTime]="view.dateTime"
                [href]="view.href"
                [imageUrl]="view.imageUrl"
                [photoCount]="view.photoCount"
                [photoCountLabel]="view.photoKey | transloco: { count: view.photoCount }"
              />
            }
          </li>
        }
      </ul>
    }
  `,
  styleUrl: './listing-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListingList {
  private readonly listingViews = inject(ListingViews);

  readonly items = input.required<readonly SearchCard[]>();
  /** Cards in a grid (desktop) rather than rows (mobile). */
  readonly grid = input(false);
  readonly loading = input(false);
  readonly skeletonCount = input(6);
  /** Accessible name of the list. */
  readonly label = input<string | null>(null);

  protected readonly views = computed(() =>
    this.items().map((card) => this.listingViews.view(card)),
  );
  protected readonly skeletons = computed(() => Array.from({ length: this.skeletonCount() }));

  constructor() {
    effect(() => this.listingViews.prepare(this.items()));
  }
}
