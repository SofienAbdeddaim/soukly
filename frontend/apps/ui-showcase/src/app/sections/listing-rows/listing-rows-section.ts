import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PricePipe, RelativeTimePipe } from '@soukly/i18n';
import { UiListingRow } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/**
 * ui-listing-row, the horizontal card of the mobile result lists: with a photo and its count,
 * without photo (placeholder) and without summary, at the width of a phone.
 */
@Component({
  selector: 'app-listing-rows-section',
  imports: [ShowcaseSection, UiListingRow, PricePipe, RelativeTimePipe],
  template: `
    <app-showcase-section [heading]="t().rows" panelClass="flex max-w-sm flex-col gap-2.5">
      @for (listing of t().listings; track $index) {
        <ui-listing-row
          [title]="listing.title"
          [price]="(listing.price | price) + listing.unit"
          [summary]="t().summaries[$index]"
          [place]="listing.city"
          [dateLabel]="createdAt(listing.hoursAgo) | relativeTime"
          [dateTime]="createdAt(listing.hoursAgo)"
          [imageUrl]="$index === 0 ? imageUrl : null"
          [photoCount]="$index === 0 ? 4 : 0"
          [photoCountLabel]="'4 ' + t().photos"
          href="#"
        />
      }
    </app-showcase-section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListingRowsSection {
  protected readonly t = inject(ShowcaseLocale).labels;
  protected readonly imageUrl = 'sample-listing.webp';
  private readonly now = Date.now();

  protected createdAt(hoursAgo: number): string {
    return new Date(this.now - hoursAgo * 3600_000).toISOString();
  }
}
