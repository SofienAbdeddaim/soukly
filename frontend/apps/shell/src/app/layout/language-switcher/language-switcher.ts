import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideChevronDown, LucideGlobe } from '@lucide/angular';
import { Lang, LANG_NAMES, LocaleService, SUPPORTED_LANGS, urlInLang } from '@soukly/i18n';
import { filter, map } from 'rxjs';

/**
 * Language switcher of the header (disclosure pattern): a button with the current language, which
 * opens the three languages, each written in its own language. A language is a link to the same
 * page with another prefix: the router changes the URL without reloading the page, and the choice
 * is remembered for the next visit to "/".
 */
@Component({
  selector: 'app-language-switcher',
  imports: [RouterLink, TranslocoPipe, LucideGlobe, LucideChevronDown],
  host: {
    class: 'relative block',
    '(focusout)': 'onFocusOut($event)',
    '(keydown.escape)': 'closeAndFocus()',
    '(document:click)': 'onDocumentClick($event)',
  },
  template: `
    <button
      #toggle
      type="button"
      class="inline-flex min-h-11 items-center gap-2 rounded-md border border-border-strong px-3 text-on-brand"
      aria-controls="language-menu"
      [attr.aria-expanded]="open()"
      data-testid="language-switcher"
      (click)="open.set(!open())"
    >
      <svg lucideGlobe [size]="18" aria-hidden="true"></svg>
      <span class="sr-only">{{ 'header.language-switcher' | transloco }}:</span>
      <span [attr.lang]="current()">{{ names[current()] }}</span>
      <svg lucideChevronDown [size]="16" aria-hidden="true"></svg>
    </button>
    <ul
      id="language-menu"
      class="absolute end-0 top-full z-20 mt-2 min-w-40 list-none rounded-md bg-surface p-1 shadow-overlay"
      [hidden]="!open()"
    >
      @for (lang of langs; track lang) {
        <li>
          <a
            class="flex min-h-11 items-center rounded-sm px-3 text-text no-underline hover:bg-surface-muted"
            [class.font-semibold]="lang === current()"
            [routerLink]="links()[lang]"
            [attr.lang]="lang"
            [attr.hreflang]="lang"
            [attr.aria-current]="lang === current() ? 'true' : null"
            (click)="choose(lang)"
          >
            {{ names[lang] }}
          </a>
        </li>
      }
    </ul>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LanguageSwitcher {
  private readonly locale = inject(LocaleService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly toggleButton = viewChild.required<ElementRef<HTMLButtonElement>>('toggle');

  protected readonly langs = SUPPORTED_LANGS;
  protected readonly names = LANG_NAMES;
  protected readonly open = signal(false);
  protected readonly current = this.locale.lang;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** The current page in each language. */
  protected readonly links = computed(
    () =>
      Object.fromEntries(
        SUPPORTED_LANGS.map((lang) => [lang, urlInLang(this.router, this.url(), lang)]),
      ) as Record<Lang, ReturnType<typeof urlInLang>>,
  );

  protected choose(lang: Lang): void {
    this.locale.remember(lang);
    this.open.set(false);
  }

  protected closeAndFocus(): void {
    if (this.open()) {
      this.open.set(false);
      this.toggleButton().nativeElement.focus();
    }
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (next && !this.host.nativeElement.contains(next)) {
      this.open.set(false);
    }
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
