import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Footer pages (help, terms of use, privacy), empty in V1: their content comes later (LEG-01 for
 * the legal pages).
 */
@Component({
  selector: 'app-static-page',
  imports: [TranslocoPipe],
  template: `
    <section class="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-12 md:px-6">
      <h1 class="text-3xl text-text">{{ titleKey() | transloco }}</h1>
      <p class="max-w-2xl text-text-muted">{{ 'static-page.coming-soon' | transloco }}</p>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaticPage {
  /** Translation key of the title, bound from the route data. */
  readonly titleKey = input.required<string>();
}
