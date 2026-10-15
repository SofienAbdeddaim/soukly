import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UiButton } from '@soukly/ui';

/**
 * Indigo header of ambiance A. Below 768px only the logo and the language slot stay: the bottom
 * bar takes over the "Se connecter" and "Déposer une annonce" actions. i18n: SETUP-07
 */
@Component({
  selector: 'app-header',
  imports: [RouterLink, UiButton],
  template: `
    <header class="bg-brand text-on-brand">
      <div class="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3 md:px-6">
        <a routerLink="/" class="font-display text-3xl leading-none text-accent no-underline">
          Soukly
        </a>
        <div class="ms-auto flex items-center gap-3">
          <!-- Language switcher, filled by SETUP-07. -->
          <div data-slot="language-switcher"></div>
          <div class="hidden items-center gap-3 md:flex" data-testid="header-actions">
            <a
              routerLink="/account/login"
              class="inline-flex min-h-11 items-center px-2 text-on-brand no-underline underline-offset-4 hover:underline"
            >
              Se connecter
            </a>
            <a ui-button routerLink="/publish/new">Déposer une annonce</a>
          </div>
        </div>
      </div>
    </header>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Header {}
