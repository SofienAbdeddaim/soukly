import { Routes } from '@angular/router';
import { withLangPrefix } from '@soukly/i18n';
import { routes } from './routes';

/** Standalone launch (pnpm nx serve mfe-publish): the exposed routes, mounted under /<lang>/publish, as in the shell. */
export const appRoutes: Routes = withLangPrefix([
  { path: '', pathMatch: 'full', redirectTo: 'publish' },
  { path: 'publish', children: routes },
]);
