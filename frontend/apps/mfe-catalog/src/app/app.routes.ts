import { Routes } from '@angular/router';
import { withLangPrefix } from '@soukly/i18n';
import { routes } from './routes';

/** Standalone launch (pnpm nx serve mfe-catalog): the exposed routes, mounted at /<lang>, as in the shell. */
export const appRoutes: Routes = withLangPrefix(routes);
