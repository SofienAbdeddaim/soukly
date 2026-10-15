import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Waiting page of mfe-catalog: proves the remote is loaded, until the feature tickets replace it.
 * Component styles only (no Tailwind utility): the page is rendered inside the shell, whose
 * stylesheet only knows the classes of the shell.
 */
@Component({
  selector: 'app-catalog-coming-soon',
  imports: [TranslocoPipe],
  template: `
    <section
      class="coming-soon"
      aria-labelledby="coming-soon-title"
      data-testid="coming-soon-mfe-catalog"
    >
      <h1 id="coming-soon-title" class="coming-soon__title">
        {{ 'catalog.coming-soon.title' | transloco }}
      </h1>
      <p class="coming-soon__text">{{ 'catalog.coming-soon.text' | transloco }}</p>
    </section>
  `,
  styleUrl: '../coming-soon.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogComingSoon {}
