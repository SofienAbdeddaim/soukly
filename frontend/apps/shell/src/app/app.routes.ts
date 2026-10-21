import { Routes, UrlMatchResult, UrlSegment } from '@angular/router';
import { withLangPrefix } from '@soukly/i18n';
import { loadRemoteRoutes } from './federation/load-remote-routes';
import { NotFound } from './not-found/not-found';
import { StaticPage } from './static-page/static-page';

/**
 * Routes of the shell, under the language prefix: "/fr/account/login", "/ar/publish/new"…
 * A URL without a supported prefix is redirected (see withLangPrefix in @soukly/i18n).
 */
export function createShellRoutes(): Routes {
  return withLangPrefix([
    { matcher: catalogPaths, loadChildren: loadRemoteRoutes('mfe-catalog') },
    { path: 'account', loadChildren: loadRemoteRoutes('mfe-account') },
    { path: 'publish', loadChildren: loadRemoteRoutes('mfe-publish') },
    { path: 'help', component: StaticPage, data: { titleKey: 'footer.help' } },
    { path: 'terms', component: StaticPage, data: { titleKey: 'footer.terms' } },
    { path: 'privacy', component: StaticPage, data: { titleKey: 'footer.privacy' } },
    { path: '**', component: NotFound },
  ]);
}

/** First segments of the pages of mfe-catalog (CATA-02), after the language prefix. */
export const CATALOG_SEGMENTS = ['search', 'c'];

/**
 * Pages served by mfe-catalog: the home page (`/{lang}`), the results (`/{lang}/search`,
 * `/{lang}/c/{category}`). Nothing is consumed: the remote matches the whole path itself, and
 * another URL never downloads it.
 */
export function catalogPaths(segments: UrlSegment[]): UrlMatchResult | null {
  return segments.length === 0 || CATALOG_SEGMENTS.includes(segments[0].path)
    ? { consumed: [] }
    : null;
}
