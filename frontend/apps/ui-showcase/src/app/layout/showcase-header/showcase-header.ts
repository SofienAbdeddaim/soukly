import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { UiButton } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';

/** Page title and the LTR/RTL toggle. */
@Component({
  selector: 'app-showcase-header',
  imports: [UiButton],
  template: `
    <header class="flex flex-wrap items-center justify-between gap-4">
      <div class="flex flex-col gap-2">
        <h1 class="text-4xl text-text">{{ locale.labels().title }}</h1>
        <p class="text-text-muted">{{ locale.labels().intro }}</p>
      </div>
      <button
        ui-button
        variant="ghost"
        type="button"
        data-testid="toggle-direction"
        lang="fr"
        (click)="locale.toggle()"
      >
        {{ locale.labels().toggleDirection }}
      </button>
    </header>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShowcaseHeader {
  protected readonly locale = inject(ShowcaseLocale);
}
