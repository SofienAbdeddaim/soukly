import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';
import { LABELS, ShowcaseLang } from './labels';

/**
 * Current language of the showcase. Switching it also sets `lang` and `dir` on <html>
 * (fr/ltr <-> ar/rtl), which is what the libs/ui components react to.
 */
@Injectable({ providedIn: 'root' })
export class ShowcaseLocale {
  private readonly document = inject(DOCUMENT);

  readonly lang = signal<ShowcaseLang>('fr');
  readonly labels = computed(() => LABELS[this.lang()]);

  toggle(): void {
    const lang: ShowcaseLang = this.lang() === 'fr' ? 'ar' : 'fr';
    this.lang.set(lang);
    const html = this.document.documentElement;
    html.lang = lang;
    html.dir = lang === 'ar' ? 'rtl' : 'ltr';
  }
}
