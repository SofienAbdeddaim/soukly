import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Indigo footer of ambiance A. Its pages are empty in V1. i18n: SETUP-07 */
@Component({
  selector: 'app-footer',
  imports: [RouterLink],
  template: `
    <footer class="bg-brand text-on-brand">
      <div
        class="mx-auto flex max-w-6xl flex-wrap items-center gap-x-8 gap-y-3 px-4 py-8 text-sm md:px-6"
      >
        <span class="font-display text-2xl leading-none text-accent">Soukly</span>
        <nav aria-label="Liens utiles">
          <ul class="m-0 flex list-none flex-wrap gap-x-8 gap-y-2 p-0">
            @for (link of links; track link.path) {
              <li>
                <a
                  [routerLink]="link.path"
                  class="inline-flex min-h-11 items-center text-on-brand underline underline-offset-4"
                >
                  {{ link.label }}
                </a>
              </li>
            }
          </ul>
        </nav>
      </div>
    </footer>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Footer {
  protected readonly links = [
    { path: '/help', label: 'Aide' },
    { path: '/terms', label: "Conditions d'utilisation" },
    { path: '/privacy', label: 'Confidentialité' },
  ];
}
