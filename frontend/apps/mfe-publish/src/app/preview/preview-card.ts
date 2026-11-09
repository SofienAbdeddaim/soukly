import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideImage, LucideMapPin, LucidePencil } from '@lucide/angular';
import { Listing } from '@soukly/listing-data';
import { UiBadge } from '@soukly/ui';

/** A specific field of the listing, ready to show. */
export interface AttributeLine {
  code: string;
  label: string;
  /** Text of the value; null for a yes/no, translated by the template. */
  value: string | null;
  checked: boolean;
}

/** Labels of the place of the listing. */
export interface PlaceLabels {
  governorate: string;
  delegation: string | null;
}

/** Links of the edit buttons, one per step that can be changed from the preview. */
export interface PreviewEditLinks {
  photos: string[];
  details: string[];
  priceLocation: string[];
}

/** Thumbnails shown under the cover; the others are counted in a "+n" tile. */
const THUMBNAILS = 4;

/**
 * The listing as buyers will see it, in one card: the cover and its thumbnails, title, price and
 * place, the specific fields, then the description. Each part carries an edit button to the step
 * where it is filled in.
 */
@Component({
  selector: 'app-preview-card',
  imports: [RouterLink, TranslocoPipe, LucideImage, LucideMapPin, LucidePencil, UiBadge],
  templateUrl: './preview-card.html',
  styleUrl: './preview-card.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PreviewCard {
  readonly listing = input.required<Listing>();
  /** "32 000 DT", or null when the listing is "on quote" or has no price yet. */
  readonly price = input<string | null>(null);
  readonly place = input<PlaceLabels | null>(null);
  readonly attributes = input<AttributeLine[]>([]);
  readonly editLinks = input.required<PreviewEditLinks>();

  protected readonly cover = computed(() => this.listing().photos[0] ?? null);
  /** Thumbnails, only when there is more than one photo. */
  protected readonly thumbnails = computed(() => {
    const photos = this.listing().photos;
    return photos.length > 1 ? photos.slice(0, THUMBNAILS) : [];
  });
  protected readonly hiddenCount = computed(() =>
    Math.max(0, this.listing().photos.length - THUMBNAILS),
  );
  protected readonly onQuote = computed(() => this.listing().priceMode === 'ON_QUOTE');
}
