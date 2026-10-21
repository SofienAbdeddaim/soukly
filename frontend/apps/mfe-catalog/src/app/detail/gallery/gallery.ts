import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideImage } from '@lucide/angular';
import { ListingPhoto } from '@soukly/listing-data';

/**
 * Photos of a listing page (CATA-03): one 4:3 photo at full width, the next ones by a horizontal
 * swipe (`scroll-snap`), a "1 / 4" counter, thumbnails from 768 px. Each photo is a button that
 * opens the full-screen viewer at it. `srcset` lets the browser pick the 400 or 1600 px WebP.
 * Without photo, a neutral picture with the name of the family.
 */
@Component({
  selector: 'app-gallery',
  imports: [TranslocoPipe, LucideImage],
  templateUrl: './gallery.html',
  styleUrl: './gallery.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Gallery {
  readonly photos = input.required<readonly ListingPhoto[]>();
  readonly title = input.required<string>();
  /** Name of the family, shown when there is no photo. */
  readonly familyLabel = input('');
  /** Index of the photo to open in the viewer. */
  readonly opened = output<number>();

  protected readonly current = signal(0);
  protected readonly count = computed(() => this.photos().length);
  private readonly track = viewChild<ElementRef<HTMLElement>>('track');

  protected srcset(photo: ListingPhoto): string {
    return `${photo.thumbnailUrl} 400w, ${photo.url} 1600w`;
  }

  /** Follows the swipe: the photo whose start is the closest to the start of the track. */
  protected onScroll(): void {
    const track = this.track()?.nativeElement;
    if (!track || !track.clientWidth) {
      return;
    }
    // scrollLeft is negative in RTL
    const index = Math.round(Math.abs(track.scrollLeft) / track.clientWidth);
    this.current.set(Math.min(Math.max(index, 0), this.count() - 1));
  }

  /** Shows a photo, from its thumbnail or when the viewer closes on another one. */
  show(index: number): void {
    const track = this.track()?.nativeElement;
    const slide = track?.children.item(index) as HTMLElement | null;
    this.current.set(index);
    slide?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest', inline: 'start' });
  }
}
