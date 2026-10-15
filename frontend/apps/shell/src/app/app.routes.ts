import { Routes } from '@angular/router';
import { loadRemoteRoutes } from './federation/load-remote-routes';
import { NotFound } from './not-found/not-found';
import { StaticPage } from './static-page/static-page';

/**
 * First-level routes of the shell. Paths are relative and have no leading slash, so SETUP-07 can
 * nest them under a /:lang prefix without rewriting them.
 * i18n: SETUP-07 (page titles of the static pages).
 */
export function createShellRoutes(): Routes {
  return [
    { path: '', pathMatch: 'full', loadChildren: loadRemoteRoutes('mfe-catalog') },
    // The "Recherche" tab of the mobile bar: served by mfe-catalog, refined by CATA-02.
    { path: 'search', loadChildren: loadRemoteRoutes('mfe-catalog') },
    { path: 'account', loadChildren: loadRemoteRoutes('mfe-account') },
    { path: 'publish', loadChildren: loadRemoteRoutes('mfe-publish') },
    { path: 'help', component: StaticPage, data: { title: 'Aide' } },
    { path: 'terms', component: StaticPage, data: { title: "Conditions d'utilisation" } },
    { path: 'privacy', component: StaticPage, data: { title: 'Confidentialité' } },
    { path: '**', component: NotFound },
  ];
}
