import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideChevronLeft, LucideChevronRight, LucideX } from '@lucide/angular';
import { LocaleService } from '@soukly/i18n';
import { ListingPhoto } from '@soukly/listing-data';

export interface PhotoViewerData {
  photos: readonly ListingPhoto[];
  title: string;
  index: number;
}

/**
 * Full-screen viewer of the photos of a listing (CDK dialog, CATA-03): the 1600 px photo, previous
 * and next buttons, the arrow keys (in reading order, so reversed in Arabic) and Escape. Closes
 * with the index of the photo shown, so the gallery shows the same one.
 */
@Component({
  selector: 'app-photo-viewer',
  imports: [TranslocoPipe, LucideChevronLeft, LucideChevronRight, LucideX],
  template: `
    <h2 id="photo-viewer-title" class="ui-visually-hidden">{{ data.title }}</h2>
    <button
      type="button"
      class="viewer__close"
      [attr.aria-label]="'catalog.actions.close' | transloco"
      (click)="close()"
    >
      <svg lucideX [size]="24" aria-hidden="true"></svg>
    </button>
    <figure class="viewer__figure">
      <img
        class="viewer__image"
        [src]="photo().url"
        [alt]="
          'catalog.detail.photo-alt'
            | transloco: { n: index() + 1, count: count, title: data.title }
        "
      />
      <figcaption class="viewer__counter" aria-live="polite" data-testid="viewer-counter">
        <bdi>{{ index() + 1 }} / {{ count }}</bdi>
      </figcaption>
    </figure>
    @if (count > 1) {
      <button
        type="button"
        class="viewer__nav viewer__nav--previous"
        [attr.aria-label]="'catalog.detail.previous-photo' | transloco"
        (click)="move(-1)"
      >
        @if (rtl()) {
          <svg lucideChevronRight [size]="28" aria-hidden="true"></svg>
        } @else {
          <svg lucideChevronLeft [size]="28" aria-hidden="true"></svg>
        }
      </button>
      <button
        type="button"
        class="viewer__nav viewer__nav--next"
        [attr.aria-label]="'catalog.detail.next-photo' | transloco"
        (click)="move(1)"
      >
        @if (rtl()) {
          <svg lucideChevronLeft [size]="28" aria-hidden="true"></svg>
        } @else {
          <svg lucideChevronRight [size]="28" aria-hidden="true"></svg>
        }
      </button>
    }
  `,
  styleUrl: './photo-viewer.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'photo-viewer', '(keydown)': 'onKeydown($event)' },
})
export class PhotoViewer {
  protected readonly data = inject<PhotoViewerData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<number, PhotoViewer>>(DialogRef);
  private readonly locale = inject(LocaleService);

  protected readonly count = this.data.photos.length;
  protected readonly index = signal(this.data.index);
  protected readonly photo = computed(() => this.data.photos[this.index()]);
  protected readonly rtl = computed(() => this.locale.dir() === 'rtl');

  constructor() {
    // Escape and a click outside close the dialog with the photo shown
    this.dialogRef.disableClose = true;
    this.dialogRef.keydownEvents.subscribe((event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        this.close();
      }
    });
  }

  /** One photo forward (1) or back (-1), from the last to the first and the other way. */
  protected move(step: number): void {
    this.index.update((index) => (index + step + this.count) % this.count);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const forward = this.rtl() ? 'ArrowLeft' : 'ArrowRight';
    const backward = this.rtl() ? 'ArrowRight' : 'ArrowLeft';
    if (event.key === forward) {
      event.preventDefault();
      this.move(1);
    } else if (event.key === backward) {
      event.preventDefault();
      this.move(-1);
    }
  }

  protected close(): void {
    this.dialogRef.close(this.index());
  }
}
