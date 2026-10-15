import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type UiSkeletonVariant = 'line' | 'block' | 'card';

/**
 * Loading placeholder, hidden from assistive technologies: the region that loads should
 * carry `aria-busy="true"` instead. The shimmer stops when the user prefers reduced motion.
 */
@Component({
  selector: 'ui-skeleton',
  templateUrl: './skeleton.html',
  styleUrl: './skeleton.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[class]': "'ui-skeleton ui-skeleton--' + variant()",
    '[style.inline-size]': 'inlineSize()',
    '[style.block-size]': 'blockSize()',
  },
})
export class UiSkeleton {
  readonly variant = input<UiSkeletonVariant>('line');
  /** CSS length, for example "60%" or "120px". */
  readonly inlineSize = input<string>();
  readonly blockSize = input<string>();
}
