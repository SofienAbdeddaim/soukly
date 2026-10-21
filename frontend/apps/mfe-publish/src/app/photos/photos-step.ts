import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucidePlus } from '@lucide/angular';
import { LocaleService } from '@soukly/i18n';
import { MAX_PHOTOS, PHOTO_TYPES } from '@soukly/listing-data';
import { UiAlert, UiButton, UiPhotoTile } from '@soukly/ui';
import { DraftStore } from '../draft/draft-store';
import { StepLayout } from '../layout/step-layout/step-layout';
import { stepCommands } from '../shared/publish-steps';
import { PhotoItem, PhotoUploads } from './photo-uploads';

/**
 * Step 2 (`/{lang}/publish/{id}/photos`): up to 10 photos, uploaded as soon as they are chosen.
 * Reordered by drag and drop (long press on a touch screen) or, with the keyboard, with the
 * "move before / after" buttons of each tile; the first photo is the cover.
 */
@Component({
  selector: 'app-photos-step',
  imports: [
    CdkDrag,
    CdkDropList,
    TranslocoPipe,
    LucidePlus,
    StepLayout,
    UiAlert,
    UiButton,
    UiPhotoTile,
  ],
  providers: [PhotoUploads],
  templateUrl: './photos-step.html',
  styleUrls: ['../shared/step-page.css', './photos-step.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhotosStep {
  private readonly store = inject(DraftStore);
  protected readonly photos = inject(PhotoUploads);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly injector = inject(Injector);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

  protected readonly max = MAX_PHOTOS;
  protected readonly accept = PHOTO_TYPES.join(',');
  protected readonly dragDelay = { touch: 300, mouse: 0 };
  /** New place (from 1) of a moved photo, announced by the polite live region. */
  protected readonly movedTo = signal<number | null>(null);
  /** Why "Continue" did not go on: no photo, or uploads still running. */
  protected readonly blocked = signal<'need-one' | 'wait' | null>(null);

  constructor() {
    const listing = this.store.listing();
    if (listing) {
      this.photos.init(listing.id, listing.photos);
    }
  }

  protected chooseFiles(): void {
    this.fileInput().nativeElement.click();
  }

  protected picked(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = [...(input.files ?? [])];
    // Emptied so that choosing the same file again fires a new change.
    input.value = '';
    this.blocked.set(null);
    this.photos.add(files);
  }

  protected async remove(item: PhotoItem): Promise<void> {
    await this.photos.remove(item.key);
    afterNextRender(
      () => this.element.querySelector<HTMLElement>('[data-testid=add-photos]')?.focus(),
      {
        injector: this.injector,
      },
    );
  }

  protected async move(item: PhotoItem, offset: -1 | 1, button: 'before' | 'after'): Promise<void> {
    const from = this.photos.ready().findIndex((ready) => ready.key === item.key);
    const order = this.photos.move(item.key, offset);
    this.announce(from + offset);
    // The tile was moved in the page, which loses the focus: put it back on the same button.
    afterNextRender(
      () => {
        const tile = this.element.querySelector(`[data-key="${item.key}"]`);
        const buttons = tile?.querySelectorAll<HTMLButtonElement>('.ui-photo-tile__move');
        const target = buttons?.[button === 'before' ? 0 : 1];
        (target && !target.disabled ? target : buttons?.[button === 'before' ? 1 : 0])?.focus();
      },
      { injector: this.injector },
    );
    await order;
  }

  protected dropped(event: CdkDragDrop<PhotoItem[]>): void {
    const last = this.photos.ready().length - 1;
    const to = Math.min(event.currentIndex, last);
    if (event.previousIndex <= last && to !== event.previousIndex) {
      void this.photos.reorder(event.previousIndex, to);
      this.announce(to);
    }
  }

  protected async next(): Promise<void> {
    if (this.photos.uploading()) {
      this.blocked.set('wait');
      return;
    }
    if (!this.photos.ready().length) {
      this.blocked.set('need-one');
      return;
    }
    this.blocked.set(null);
    const id = this.store.id();
    if (id && (await this.store.flush())) {
      await this.router.navigate(stepCommands(this.locale.lang(), id, 'details'));
    }
  }

  private announce(index: number): void {
    this.movedTo.set(index + 1);
  }
}
