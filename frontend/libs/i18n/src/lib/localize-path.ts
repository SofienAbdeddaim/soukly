import { inject, Pipe, PipeTransform } from '@angular/core';
import { Router, UrlTree } from '@angular/router';
import { isLang, Lang } from './languages';
import { LocaleService } from './locale.service';

/** "/account/login" -> "/ar/account/login"; "/" -> "/ar". The path starts with "/". */
export function localizePath(path: string, lang: Lang): string {
  return path === '/' || path === '' ? `/${lang}` : `/${lang}${path}`;
}

/**
 * The same page in another language: the language prefix is replaced (or added), the rest of
 * the path, the query parameters and the fragment are kept.
 */
export function urlInLang(router: Router, url: string, lang: Lang): UrlTree {
  const tree = router.parseUrl(url);
  const segments = (tree.root.children['primary']?.segments ?? []).map((segment) => segment.path);
  const rest = isLang(segments[0]) ? segments.slice(1) : segments;
  return router.createUrlTree(['/', lang, ...rest], {
    queryParams: tree.queryParams,
    fragment: tree.fragment ?? undefined,
  });
}

/**
 * Prefixes an absolute app path with the current language, for `routerLink`:
 * `[routerLink]="'/account/login' | localePath"`. Impure: it follows the language signal.
 */
@Pipe({ name: 'localePath', pure: false })
export class LocalePathPipe implements PipeTransform {
  private readonly locale = inject(LocaleService);

  transform(path: string): string {
    return localizePath(path, this.locale.lang());
  }
}
