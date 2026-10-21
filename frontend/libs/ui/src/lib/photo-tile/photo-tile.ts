import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import {
  LucideChevronLeft,
  LucideChevronRight,
  LucideImage,
  LucideRotateCw,
  LucideX,
} from '@lucide/angular';
import { UiProgress } from '../progress/progress';

export type UiPhotoTileState = 'ready' | 'uploading' | 'failed';

/**
 * Square thumbnail of a photo being added to a listing:
 *
 * ```html
 * <ui-photo-tile [src]="url" alt="Photo 1 of 3" cover coverLabel="Cover" removeLabel="Delete" … />
 * ```
 *
 * - `ready`: the photo, a "cover" badge on the first one, a delete button and two buttons to
 *   move it before or after its neighbour, the keyboard alternative to drag and drop: they are
 *   shown when the tile has the focus or the pointer, and always reachable by assistive
 *   technologies;
 * - `uploading`: a progress bar over the photo;
 * - `failed`: a short error and a retry button (`retryable`).
 *
 * Buttons are at least 44px. All texts come from inputs.
 */
@Component({
  selector: 'ui-photo-tile',
  imports: [
    LucideChevronLeft,
    LucideChevronRight,
    LucideImage,
    LucideRotateCw,
    LucideX,
    UiProgress,
  ],
  templateUrl: './photo-tile.html',
  styleUrl: './photo-tile.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': "'ui-photo-tile ui-photo-tile--' + state()",
  },
})
export class UiPhotoTile {
  /** Thumbnail URL (or an object URL of the local file while it uploads); placeholder without. */
  readonly src = input<string | null>(null);
  readonly alt = input.required<string>();
  readonly state = input<UiPhotoTileState>('ready');
  readonly cover = input(false, { transform: booleanAttribute });
  readonly coverLabel = input('');
  /** From 0 to 100, while `uploading`. */
  readonly progress = input(0);
  readonly progressLabel = input('');
  readonly errorText = input<string | null>(null);
  readonly retryable = input(false, { transform: booleanAttribute });
  readonly retryLabel = input('');
  readonly removable = input(true, { transform: booleanAttribute });
  readonly removeLabel = input.required<string>();
  readonly canMoveBefore = input(false, { transform: booleanAttribute });
  readonly canMoveAfter = input(false, { transform: booleanAttribute });
  readonly moveBeforeLabel = input('');
  readonly moveAfterLabel = input('');

  readonly remove = output<void>();
  readonly retry = output<void>();
  readonly moveBefore = output<void>();
  readonly moveAfter = output<void>();
}
