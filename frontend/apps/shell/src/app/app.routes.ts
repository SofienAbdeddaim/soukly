import { Routes } from '@angular/router';
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
    { path: '', pathMatch: 'full', loadChildren: loadRemoteRoutes('mfe-catalog') },
    // The "Search" tab of the mobile bar: served by mfe-catalog, refined by CATA-02.
    { path: 'search', loadChildren: loadRemoteRoutes('mfe-catalog') },
    { path: 'account', loadChildren: loadRemoteRoutes('mfe-account') },
    { path: 'publish', loadChildren: loadRemoteRoutes('mfe-publish') },
    { path: 'help', component: StaticPage, data: { titleKey: 'footer.help' } },
    { path: 'terms', component: StaticPage, data: { titleKey: 'footer.terms' } },
    { path: 'privacy', component: StaticPage, data: { titleKey: 'footer.privacy' } },
    { path: '**', component: NotFound },
  ]);
}
