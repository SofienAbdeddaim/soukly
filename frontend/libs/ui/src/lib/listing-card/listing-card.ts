import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import { LucideHeart, LucideImage } from '@lucide/angular';

/**
 * Listing card of the catalogue. Every text arrives already formatted and translated
 * (price, city, date, labels): the card only lays them out. Without `imageUrl` it shows
 * a placeholder. The favourite button only reflects `favorite` and emits `favoriteToggle`.
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
  /** Formatted price, for example "1 250 DT"; always rendered left-to-right. */
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
  /** Accessible name of the favourite button, for example "Add to favourites". */
  readonly favoriteLabel = input.required<string>();

  readonly favoriteToggle = output<boolean>();

  protected toggleFavorite(): void {
    this.favoriteToggle.emit(!this.favorite());
  }
}
