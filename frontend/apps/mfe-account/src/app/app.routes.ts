import { Routes } from '@angular/router';
import { withLangPrefix } from '@soukly/i18n';
import { routes } from './routes';

/** Standalone launch (pnpm nx serve mfe-account): the exposed routes, mounted under /<lang>/account, as in the shell. */
export const appRoutes: Routes = withLangPrefix([
  { path: '', pathMatch: 'full', redirectTo: 'account' },
  { path: 'account', children: routes },
]);
