import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideHouse, LucidePlus, LucideSearch, LucideUser } from '@lucide/angular';
import { LocalePathPipe } from '@soukly/i18n';

/**
 * Bottom navigation bar below 768px, as on mockup A mobile. "Post" is highlighted.
 * "Favourites" is hidden while no V1 ticket covers favourites.
 */
@Component({
  selector: 'app-bottom-nav',
  imports: [
    RouterLink,
    RouterLinkActive,
    TranslocoPipe,
    LocalePathPipe,
    LucideHouse,
    LucidePlus,
    LucideSearch,
    LucideUser,
  ],
  template: `
    <nav
      [attr.aria-label]="'bottom-nav.label' | transloco"
      class="fixed inset-x-0 bottom-0 z-10 border-t border-border bg-surface md:hidden"
      data-testid="bottom-nav"
    >
      <ul class="m-0 grid list-none grid-cols-4 p-0">
        <li>
          <a
            [routerLink]="'/' | localePath"
            routerLinkActive="text-primary font-semibold"
            ariaCurrentWhenActive="page"
            [routerLinkActiveOptions]="{ exact: true }"
            class="flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs text-text-muted no-underline"
          >
            <svg lucideHouse [size]="22" aria-hidden="true"></svg>
            {{ 'bottom-nav.home' | transloco }}
          </a>
        </li>
        <li>
          <a
            [routerLink]="'/search' | localePath"
            routerLinkActive="text-primary font-semibold"
            ariaCurrentWhenActive="page"
            class="flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs text-text-muted no-underline"
          >
            <svg lucideSearch [size]="22" aria-hidden="true"></svg>
            {{ 'bottom-nav.search' | transloco }}
          </a>
        </li>
        <li>
          <a
            [routerLink]="'/publish/new' | localePath"
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
            {{ 'bottom-nav.publish' | transloco }}
          </a>
        </li>
        <li>
          <a
            [routerLink]="'/account/profile' | localePath"
            routerLinkActive="text-primary font-semibold"
            ariaCurrentWhenActive="page"
            class="flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs text-text-muted no-underline"
          >
            <svg lucideUser [size]="22" aria-hidden="true"></svg>
            {{ 'bottom-nav.account' | transloco }}
          </a>
        </li>
      </ul>
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BottomNav {}
