import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import { LucideHeart, LucideImage } from '@lucide/angular';

/**
 * Listing card of the catalogue. Every text arrives already formatted and translated
 * (price, city, date, labels): the card only lays them out. Without `imageUrl` it shows
 * a placeholder. The favourite button only reflects `favorite` and emits `favoriteToggle`; it is
 * shown only with a `favoriteLabel` (favourites are not part of V1, CATA-02).
 */
@Component({
  selector: 'ui-listing-card',
  imports: [LucideHeart, LucideImage],
  templateUrl: './listing-card.html',
  styleUrl: './listing-card.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UiListingCard {
  readonly title = input.required<string>();
  /**
   * Formatted price (`price` pipe of @soukly/i18n), for example "1 250 DT" or "1.250 د.ت.":
   * isolated from the surrounding text, in the direction of its own first letter.
   */
  readonly price = input.required<string>();
  readonly city = input.required<string>();
  /** Human date, for example "2 h ago". */
  readonly dateLabel = input.required<string>();
  /** Machine date for the `time` element (ISO 8601). */
  readonly dateTime = input<string>();
  /** Listing type badge, for example "Sale" or "Service". */
  readonly typeLabel = input<string>();
  readonly href = input<string>();
  readonly imageUrl = input<string | null>();
  readonly favorite = input(false, { transform: booleanAttribute });
  /** Accessible name of the favourite button, for example "Add to favourites"; no button without it. */
  readonly favoriteLabel = input<string | null>(null);

  readonly favoriteToggle = output<boolean>();

  protected toggleFavorite(): void {
    this.favoriteToggle.emit(!this.favorite());
  }
}
