import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Footer pages (Aide, Conditions d'utilisation, Confidentialité), empty in V1: their content comes
 * later (LEG-01 for the legal pages). i18n: SETUP-07
 */
@Component({
  selector: 'app-static-page',
  template: `
    <section class="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-12 md:px-6">
      <h1 class="text-3xl text-text">{{ title() }}</h1>
      <p class="max-w-2xl text-text-muted">Le contenu de cette page sera bientôt disponible.</p>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaticPage {
  /** Bound from the route data. */
  readonly title = input.required<string>();
}
