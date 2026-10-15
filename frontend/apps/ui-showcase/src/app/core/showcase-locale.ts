import { Injectable, computed, inject, signal } from '@angular/core';
import { LocaleService } from '@soukly/i18n';
import { LABELS, ShowcaseLang } from './labels';

/**
 * Current language of the showcase. Switching it also switches the LocaleService of @soukly/i18n,
 * which sets `lang` and `dir` on <html> (fr/ltr <-> ar/rtl) and the locale of the format pipes.
 */
@Injectable({ providedIn: 'root' })
export class ShowcaseLocale {
  private readonly locale = inject(LocaleService);

  readonly lang = signal<ShowcaseLang>('fr');
  readonly labels = computed(() => LABELS[this.lang()]);

  toggle(): void {
    const lang: ShowcaseLang = this.lang() === 'fr' ? 'ar' : 'fr';
    this.lang.set(lang);
    this.locale.use(lang);
  }
}
