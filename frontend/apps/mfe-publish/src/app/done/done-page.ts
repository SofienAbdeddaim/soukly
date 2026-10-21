import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideCircleCheck } from '@lucide/angular';
import { LocalePathPipe } from '@soukly/i18n';
import { UiButton } from '@soukly/ui';

/**
 * Confirmation after the publication (`/{lang}/publish/{id}/done`), outside the draft page: the
 * listing is no longer a draft. Links to its public page (CATA-03) and to a new listing.
 */
@Component({
  selector: 'app-done-page',
  imports: [RouterLink, TranslocoPipe, LocalePathPipe, LucideCircleCheck, UiButton],
  template: `
    <section class="done" data-testid="publish-done">
      <svg lucideCircleCheck class="done__icon" [size]="56" aria-hidden="true"></svg>
      <h1 class="done__title" tabindex="-1" #heading>{{ 'publish.done.title' | transloco }}</h1>
      <p class="done__text">{{ 'publish.done.text' | transloco }}</p>
      <div class="done__actions">
        <a ui-button size="lg" [routerLink]="'/listings/' + id() | localePath">
          {{ 'publish.done.view' | transloco }}
        </a>
        <a ui-button variant="secondary" size="lg" [routerLink]="'/publish/new' | localePath">
          {{ 'publish.done.another' | transloco }}
        </a>
      </div>
    </section>
  `,
  styles: `
    :host {
      display: block;
    }

    .done {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      max-inline-size: 640px;
      margin-inline: auto;
      padding-block: 48px;
      padding-inline: 16px;
      text-align: center;
    }

    .done__icon {
      color: var(--color-success);
    }

    .done__title {
      font-size: 1.75rem;
    }

    .done__text {
      color: var(--color-text-muted);
    }

    .done__actions {
      display: flex;
      flex-direction: column;
      align-self: stretch;
      gap: 10px;
      margin-block-start: 12px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DonePage {
  /** From the route parameter (`withComponentInputBinding`). */
  readonly id = input.required<string>();

  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus({ preventScroll: true }));
  }
}
