import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocalePathPipe } from '@soukly/i18n';
import { UiButton } from '@soukly/ui';
import { LanguageSwitcher } from '../language-switcher/language-switcher';

/**
 * Indigo header of ambiance A. Below 768px only the logo and the language switcher stay: the
 * bottom bar takes over the "log in" and "post an ad" actions.
 */
@Component({
  selector: 'app-header',
  imports: [RouterLink, UiButton, TranslocoPipe, LocalePathPipe, LanguageSwitcher],
  template: `
    <header class="bg-brand text-on-brand">
      <div class="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3 md:px-6">
        <a
          [routerLink]="'/' | localePath"
          class="font-display text-3xl leading-none text-accent no-underline"
        >
          {{ 'brand' | transloco }}
        </a>
        <div class="ms-auto flex items-center gap-3">
          <app-language-switcher />
          <div class="hidden items-center gap-3 md:flex" data-testid="header-actions">
            <a
              [routerLink]="'/account/login' | localePath"
              class="inline-flex min-h-11 items-center px-2 text-on-brand no-underline underline-offset-4 hover:underline"
            >
              {{ 'header.login' | transloco }}
            </a>
            <a ui-button [routerLink]="'/publish/new' | localePath">
              {{ 'header.post-listing' | transloco }}
            </a>
          </div>
        </div>
      </div>
    </header>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Header {}
