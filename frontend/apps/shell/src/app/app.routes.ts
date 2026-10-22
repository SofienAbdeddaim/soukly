import { Routes, UrlMatchResult, UrlSegment } from '@angular/router';
import { withLangPrefix } from '@soukly/i18n';
import { loadRemoteRoutes } from './federation/load-remote-routes';
import { NotFound } from './not-found/not-found';

/**
 * Routes of the shell, under the language prefix: "/fr/account/login", "/ar/publish/new"…
 * A URL without a supported prefix is redirected (see withLangPrefix in @soukly/i18n).
 * The legal pages (`/{lang}/legal/{slug}`, LEG-01) are static HTML served before the shell: an
 * unknown one reaches the shell and shows its 404 page.
 */
export function createShellRoutes(): Routes {
  return withLangPrefix([
    { matcher: catalogPaths, loadChildren: loadRemoteRoutes('mfe-catalog') },
    { path: 'account', loadChildren: loadRemoteRoutes('mfe-account') },
    { path: 'publish', loadChildren: loadRemoteRoutes('mfe-publish') },
    { path: '**', component: NotFound },
  ]);
}

/** First segments of the pages of mfe-catalog (CATA-02, CATA-03), after the language prefix. */
export const CATALOG_SEGMENTS = ['search', 'c', 'listings'];

/**
 * Pages served by mfe-catalog: the home page (`/{lang}`), the results (`/{lang}/search`,
 * `/{lang}/c/{category}`) and the listing pages (`/{lang}/listings/{id}`). Nothing is consumed: the remote matches the whole path itself, and
 * another URL never downloads it.
 */
export function catalogPaths(segments: UrlSegment[]): UrlMatchResult | null {
  return segments.length === 0 || CATALOG_SEGMENTS.includes(segments[0].path)
    ? { consumed: [] }
    : null;
}
