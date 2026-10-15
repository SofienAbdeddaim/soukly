import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { PricePipe, RelativeTimePipe } from '@soukly/i18n';
import { UiListingCard } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/**
 * ui-listing-card with an image, without image (placeholder) and as a favourite. The price and the
 * date are formatted by the pipes of @soukly/i18n in the current language.
 */
@Component({
  selector: 'app-listing-cards-section',
  imports: [ShowcaseSection, UiListingCard, PricePipe, RelativeTimePipe],
  templateUrl: './listing-cards-section.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListingCardsSection {
  protected readonly t = inject(ShowcaseLocale).labels;
  protected readonly favorites = signal([false, true, false]);
  protected readonly imageUrl = 'sample-listing.webp';
  private readonly now = Date.now();

  protected createdAt(hoursAgo: number): string {
    return new Date(this.now - hoursAgo * 3600_000).toISOString();
  }

  protected setFavorite(index: number, value: boolean): void {
    this.favorites.update((favorites) => favorites.map((f, i) => (i === index ? value : f)));
  }
}
