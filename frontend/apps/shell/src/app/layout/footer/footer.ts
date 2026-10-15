import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocalePathPipe } from '@soukly/i18n';

/** Indigo footer of ambiance A. Its pages are empty in V1. */
@Component({
  selector: 'app-footer',
  imports: [RouterLink, TranslocoPipe, LocalePathPipe],
  template: `
    <footer class="bg-brand text-on-brand">
      <div
        class="mx-auto flex max-w-6xl flex-wrap items-center gap-x-8 gap-y-3 px-4 py-8 text-sm md:px-6"
      >
        <span class="font-display text-2xl leading-none text-accent">{{
          'brand' | transloco
        }}</span>
        <nav [attr.aria-label]="'footer.label' | transloco">
          <ul class="m-0 flex list-none flex-wrap gap-x-8 gap-y-2 p-0">
            @for (link of links; track link.path) {
              <li>
                <a
                  [routerLink]="link.path | localePath"
                  class="inline-flex min-h-11 items-center text-on-brand underline underline-offset-4"
                >
                  {{ link.labelKey | transloco }}
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
    { path: '/help', labelKey: 'footer.help' },
    { path: '/terms', labelKey: 'footer.terms' },
    { path: '/privacy', labelKey: 'footer.privacy' },
  ];
}
