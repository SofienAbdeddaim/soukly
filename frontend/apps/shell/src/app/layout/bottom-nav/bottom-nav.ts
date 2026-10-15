import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LucideHouse, LucidePlus, LucideSearch, LucideUser } from '@lucide/angular';

/**
 * Bottom navigation bar below 768px, as on mockup A mobile. "Déposer" is highlighted.
 * "Favoris" is hidden while no V1 ticket covers favourites. i18n: SETUP-07
 */
@Component({
  selector: 'app-bottom-nav',
  imports: [RouterLink, RouterLinkActive, LucideHouse, LucidePlus, LucideSearch, LucideUser],
  template: `
    <nav
      aria-label="Navigation principale"
      class="fixed inset-x-0 bottom-0 z-10 border-t border-border bg-surface md:hidden"
      data-testid="bottom-nav"
    >
      <ul class="m-0 grid list-none grid-cols-4 p-0">
        <li>
          <a
            routerLink="/"
            routerLinkActive="text-primary font-semibold"
            ariaCurrentWhenActive="page"
            [routerLinkActiveOptions]="{ exact: true }"
            class="flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs text-text-muted no-underline"
          >
            <svg lucideHouse [size]="22" aria-hidden="true"></svg>
            Accueil
          </a>
        </li>
        <li>
          <a
            routerLink="/search"
            routerLinkActive="text-primary font-semibold"
            ariaCurrentWhenActive="page"
            class="flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs text-text-muted no-underline"
          >
            <svg lucideSearch [size]="22" aria-hidden="true"></svg>
            Recherche
          </a>
        </li>
        <li>
          <a
            routerLink="/publish/new"
            routerLinkActive="font-semibold"
            ariaCurrentWhenActive="page"
            class="flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs text-text no-underline"
            data-testid="bottom-nav-publish"
          >
            <span
              class="-mt-5 grid size-12 place-items-center rounded-full bg-primary text-on-primary shadow-card"
            >
              <svg lucidePlus [size]="24" [strokeWidth]="2.4" aria-hidden="true"></svg>
            </span>
            Déposer
          </a>
        </li>
        <li>
          <a
            routerLink="/account"
            routerLinkActive="text-primary font-semibold"
            ariaCurrentWhenActive="page"
            class="flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs text-text-muted no-underline"
          >
            <svg lucideUser [size]="22" aria-hidden="true"></svg>
            Compte
          </a>
        </li>
      </ul>
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BottomNav {}
