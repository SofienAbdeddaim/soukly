import { ChangeDetectionStrategy, Component, input } from '@angular/core';

let nextSectionId = 0;

/** Titled section of the showcase page; its content is projected into a panel. */
@Component({
  selector: 'app-showcase-section',
  template: `
    <section class="flex flex-col gap-4" [attr.aria-labelledby]="titleId">
      <h2 class="text-2xl" [id]="titleId">{{ heading() }}</h2>
      <div [class]="panelClass()">
        <ng-content />
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShowcaseSection {
  readonly heading = input.required<string>();
  /** Layout of the panel; the default is a white card with wrapping items. */
  readonly panelClass = input(
    'flex flex-wrap items-center gap-3 rounded-lg bg-surface p-6 shadow-card',
  );

  protected readonly titleId = `showcase-section-${nextSectionId++}`;
}
