import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { UiListingCard } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/** ui-listing-card with an image, without image (placeholder) and as a favourite. */
@Component({
  selector: 'app-listing-cards-section',
  imports: [ShowcaseSection, UiListingCard],
  templateUrl: './listing-cards-section.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListingCardsSection {
  protected readonly t = inject(ShowcaseLocale).labels;
  protected readonly favorites = signal([false, true, false]);
  protected readonly imageUrl = 'sample-listing.webp';

  protected setFavorite(index: number, value: boolean): void {
    this.favorites.update((favorites) => favorites.map((f, i) => (i === index ? value : f)));
  }
}
