import { Routes } from '@angular/router';
import { CatalogComingSoon } from './coming-soon/catalog-coming-soon';

/**
 * Routes exposed to the shell as "./routes" (federation.config.mjs), relative to the path the
 * shell mounts them on.
 */
export const routes: Routes = [{ path: '', pathMatch: 'full', component: CatalogComingSoon }];
