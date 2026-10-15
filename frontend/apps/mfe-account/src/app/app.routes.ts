import { Routes } from '@angular/router';
import { routes } from './routes';

/** Standalone launch (pnpm nx serve mfe-account): the exposed routes, mounted under /account, as in the shell. */
export const appRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'account' },
  { path: 'account', children: routes },
];
