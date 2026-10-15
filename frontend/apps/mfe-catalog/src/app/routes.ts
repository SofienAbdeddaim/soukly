import { Routes } from '@angular/router';
import { provideI18nScope } from '@soukly/i18n';
import { CatalogComingSoon } from './coming-soon/catalog-coming-soon';

/**
 * Routes exposed to the shell as "./routes" (federation.config.mjs), relative to the path the
 * shell mounts them on. The wrapping route provides the "catalog" translations, downloaded from this
 * remote (public/i18n/catalog) when one of its pages is shown.
 */
export const routes: Routes = [
  {
    path: '',
    providers: [provideI18nScope('catalog', import.meta.url)],
    children: [{ path: '', pathMatch: 'full', component: CatalogComingSoon }],
  },
];
