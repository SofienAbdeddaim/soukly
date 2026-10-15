import { DestroyRef, DOCUMENT, inject, Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { DEFAULT_LANG, SUPPORTED_LANGS } from './languages';
import { urlInLang } from './localize-path';

/**
 * Keeps `<link rel="alternate" hreflang="fr|ar|en|x-default">` in <head> pointing to the three
 * language variants of the current page (x-default: French). Absolute URLs on the origin of the
 * page: the domain is never hard-coded.
 */
@Injectable({ providedIn: 'root' })
export class AlternateLinks {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  start(): void {
    const subscription = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.update(this.router.url));
    this.destroyRef.onDestroy(() => subscription.unsubscribe());
  }

  update(url: string): void {
    const head = this.document.head;
    head.querySelectorAll('link[rel="alternate"][hreflang]').forEach((link) => link.remove());
    const origin = this.document.location.origin;
    const variants = [
      ...SUPPORTED_LANGS.map((lang) => [lang, lang] as const),
      ['x-default', DEFAULT_LANG] as const,
    ];
    for (const [hreflang, lang] of variants) {
      const link = this.document.createElement('link');
      link.rel = 'alternate';
      link.hreflang = hreflang;
      link.href = origin + this.router.serializeUrl(urlInLang(this.router, url, lang));
      head.appendChild(link);
    }
  }
}
