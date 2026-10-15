import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocalePathPipe } from '@soukly/i18n';
import { UiButton } from '@soukly/ui';

/** Page of the shell for any unknown URL. */
@Component({
  selector: 'app-not-found',
  imports: [RouterLink, UiButton, TranslocoPipe, LocalePathPipe],
  template: `
    <section
      class="mx-auto flex max-w-6xl flex-col items-start gap-4 px-4 py-12 md:px-6"
      aria-labelledby="not-found-title"
      data-testid="not-found"
    >
      <h1 id="not-found-title" class="text-3xl text-text">
        {{ 'errors.not-found.title' | transloco }}
      </h1>
      <p class="max-w-2xl text-text-muted">{{ 'errors.not-found.text' | transloco }}</p>
      <a ui-button [routerLink]="'/' | localePath">{{ 'errors.not-found.home' | transloco }}</a>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFound {}
