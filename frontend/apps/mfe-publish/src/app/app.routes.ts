import { Routes } from '@angular/router';
import { routes } from './routes';

/** Standalone launch (pnpm nx serve mfe-publish): the exposed routes, mounted under /publish, as in the shell. */
export const appRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'publish' },
  { path: 'publish', children: routes },
];
