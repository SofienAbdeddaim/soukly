import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft } from '@lucide/angular';
import { LocalePathPipe } from '@soukly/i18n';

/**
 * Brand band at the top of the profile screens (AUTH-05): a back link and the title of the page,
 * the `h1`. `back` is a path without the language ("/account/profile").
 */
@Component({
  selector: 'app-page-header',
  imports: [RouterLink, LocalePathPipe, LucideArrowLeft],
  template: `
    <a
      class="page-header__back"
      [routerLink]="back() | localePath"
      [attr.aria-label]="backLabel()"
      data-testid="page-header-back"
    >
      <svg lucideArrowLeft class="ui-flip-rtl" [size]="22" aria-hidden="true"></svg>
    </a>
    <h1 class="page-header__title" [id]="titleId()">{{ title() }}</h1>
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 6px;
      padding-block: 10px;
      padding-inline: 16px;
      background-color: var(--color-brand);
      color: var(--color-on-brand);
    }

    .page-header__back {
      display: grid;
      flex: none;
      place-items: center;
      inline-size: var(--control-height);
      block-size: var(--control-height);
      margin-inline-start: -10px;
      border-radius: var(--radius-full);
      color: var(--color-on-brand);
    }

    .page-header__back:hover {
      color: var(--color-accent);
    }

    .page-header__title {
      font-family: var(--font-display);
      font-size: 1.5rem;
      font-weight: 400;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly titleId = input.required<string>();
  readonly back = input.required<string>();
  readonly backLabel = input.required<string>();
}
