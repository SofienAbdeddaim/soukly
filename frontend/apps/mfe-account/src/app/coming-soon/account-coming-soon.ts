import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Waiting page of mfe-account: proves the remote is loaded, until the feature tickets replace it.
 * Component styles only (no Tailwind utility): the page is rendered inside the shell, whose
 * stylesheet only knows the classes of the shell. i18n: SETUP-07
 */
@Component({
  selector: 'app-account-coming-soon',
  template: `
    <section
      class="coming-soon"
      aria-labelledby="coming-soon-title"
      data-testid="coming-soon-mfe-account"
    >
      <h1 id="coming-soon-title" class="coming-soon__title">
        Espace compte Soukly, bientôt disponible
      </h1>
      <p class="coming-soon__text">Inscription, connexion et profil arrivent bientôt ici.</p>
    </section>
  `,
  styleUrl: '../coming-soon.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountComingSoon {}
