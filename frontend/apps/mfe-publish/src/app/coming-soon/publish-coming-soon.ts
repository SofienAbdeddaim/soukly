import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Waiting page of mfe-publish: proves the remote is loaded, until the feature tickets replace it.
 * Component styles only (no Tailwind utility): the page is rendered inside the shell, whose
 * stylesheet only knows the classes of the shell. i18n: SETUP-07
 */
@Component({
  selector: 'app-publish-coming-soon',
  template: `
    <section
      class="coming-soon"
      aria-labelledby="coming-soon-title"
      data-testid="coming-soon-mfe-publish"
    >
      <h1 id="coming-soon-title" class="coming-soon__title">
        Dépôt d'annonces Soukly, bientôt disponible
      </h1>
      <p class="coming-soon__text">Le dépôt et la gestion de vos annonces arrivent bientôt ici.</p>
    </section>
  `,
  styleUrl: '../coming-soon.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublishComingSoon {}
