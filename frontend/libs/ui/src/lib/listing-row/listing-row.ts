import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { LucideCamera, LucideImage } from '@lucide/angular';

/**
 * Horizontal listing card of the mobile result lists (CATA-02): 4:3 photo at the start with its
 * number of photos, then the title (2 lines at most), the price, the summary and the place with
 * the date. Every text arrives already formatted and translated; the card only lays them out.
 * The whole card is clickable through the title link.
 */
@Component({
  selector: 'ui-listing-row',
  imports: [LucideCamera, LucideImage],
  templateUrl: './listing-row.html',
  styleUrl: './listing-row.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UiListingRow {
  readonly title = input.required<string>();
  /** Formatted price, for example "32 000 DT", "1.100 د.ت. / شهر" or "Sur devis". */
  readonly price = input.required<string>();
  /** Specific attributes joined by " · ", for example "2019 · 62 000 km · Diesel". */
  readonly summary = input<string | null>();
  /** Place of the listing, for example "Sfax, Sakiet Ezzit". */
  readonly place = input.required<string>();
  /** Human date, for example "il y a 2 h". */
  readonly dateLabel = input.required<string>();
  /** Machine date for the `time` element (ISO 8601). */
  readonly dateTime = input<string>();
  readonly href = input<string>();
  readonly imageUrl = input<string | null>();
  readonly photoCount = input<number | null>(null);
  /** Accessible text of the photo count, for example "4 photos". */
  readonly photoCountLabel = input<string | null>(null);
}
