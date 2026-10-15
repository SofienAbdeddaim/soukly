import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UiButton } from '@soukly/ui';

/** Page of the shell for any unknown URL. i18n: SETUP-07 */
@Component({
  selector: 'app-not-found',
  imports: [RouterLink, UiButton],
  template: `
    <section
      class="mx-auto flex max-w-6xl flex-col items-start gap-4 px-4 py-12 md:px-6"
      aria-labelledby="not-found-title"
      data-testid="not-found"
    >
      <h1 id="not-found-title" class="text-3xl text-text">Page introuvable</h1>
      <p class="max-w-2xl text-text-muted">
        Cette adresse ne correspond à aucune page de Soukly. Elle a peut-être été déplacée.
      </p>
      <a ui-button routerLink="/">Retour à l'accueil</a>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFound {}
