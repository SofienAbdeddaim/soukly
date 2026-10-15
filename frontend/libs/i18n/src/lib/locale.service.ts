import { computed, DOCUMENT, inject, Injectable, signal } from '@angular/core';
import {
  DEFAULT_LANG,
  directionOf,
  firstSupportedLang,
  INTL_LOCALES,
  isLang,
  Lang,
  looksLikeLangCode,
} from './languages';
import { readStoredLang, storeLang } from './lang-storage';

/**
 * Current language of the page: a signal, its text direction and its `Intl` locale. `use()` also
 * sets `lang` and `dir` on <html>. The URL prefix is the source of truth: the language guard calls
 * `use()` on every navigation, the language switcher only changes the URL.
 */
@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly document = inject(DOCUMENT);
  private readonly current = signal<Lang>(DEFAULT_LANG);

  readonly lang = this.current.asReadonly();
  readonly dir = computed(() => directionOf(this.current()));
  readonly intlLocale = computed(() => INTL_LOCALES[this.current()]);

  use(lang: Lang): void {
    this.current.set(lang);
    const html = this.document.documentElement;
    html.lang = lang;
    html.dir = directionOf(lang);
  }

  /** Remembers an explicit choice of the visitor (language switcher). */
  remember(lang: Lang): void {
    storeLang(lang, this.storage());
  }

  /** Language for a URL without prefix: remembered choice, then browser languages, then French. */
  preferredLang(): Lang {
    const navigator = this.document.defaultView?.navigator;
    return (
      readStoredLang(this.storage()) ??
      firstSupportedLang(
        navigator?.languages ?? (navigator?.language ? [navigator.language] : []),
      ) ??
      DEFAULT_LANG
    );
  }

  /**
   * Language a URL is displayed in, from its first segment: the segment itself when supported,
   * French for an unknown language code ("/de/…"), the preferred language when there is no prefix.
   */
  langForSegment(segment: string | undefined): Lang {
    if (isLang(segment)) {
      return segment;
    }
    return looksLikeLangCode(segment) ? DEFAULT_LANG : this.preferredLang();
  }

  private storage(): Storage | undefined {
    try {
      return this.document.defaultView?.localStorage;
    } catch {
      return undefined;
    }
  }
}
