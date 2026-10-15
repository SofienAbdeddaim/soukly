import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { UiSkeleton } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/** ui-skeleton: listing card, block and lines, in a region marked aria-busy. */
@Component({
  selector: 'app-skeletons-section',
  imports: [ShowcaseSection, UiSkeleton],
  template: `
    <app-showcase-section
      [heading]="t().skeletons"
      panelClass="grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-5"
    >
      <ui-skeleton variant="card" />
      <div class="flex flex-col gap-3 rounded-lg bg-surface p-6" aria-busy="true">
        <ui-skeleton variant="block" blockSize="96px" />
        <ui-skeleton />
        <ui-skeleton inlineSize="70%" />
        <ui-skeleton inlineSize="40%" />
      </div>
    </app-showcase-section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkeletonsSection {
  protected readonly t = inject(ShowcaseLocale).labels;
}
