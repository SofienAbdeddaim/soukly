import { Routes, UrlMatchResult, UrlSegment } from '@angular/router';
import { provideI18nScope } from '@soukly/i18n';
import { DetailPage } from './detail/detail-page';
import { HomePage } from './home/home-page';
import { ResultsPage } from './results/results-page';

/**
 * Routes exposed to the shell as "./routes" (federation.config.mjs). The shell mounts them right
 * under the language prefix, for `/{lang}/`, `/{lang}/search`, `/{lang}/c/…` and
 * `/{lang}/listings/…` only. The wrapping
 * route provides the "catalog" translations, downloaded from this remote (public/i18n/catalog)
 * when one of its pages is shown.
 *
 * - ``: home page;
 * - `search`: results, every criterion in the query parameters;
 * - `c/{categoryCode}`: results of a family or of a subcategory (`/fr/c/vehicles.cars`), same page;
 * - `listings/{id}`: page of a listing (CATA-03), the address of ANN-03 and of the social posts.
 */
export const routes: Routes = [
  {
    path: '',
    providers: [provideI18nScope('catalog', import.meta.url)],
    children: [
      { path: '', pathMatch: 'full', component: HomePage },
      { matcher: resultsPaths, component: ResultsPage },
      { path: 'listings/:id', component: DetailPage },
    ],
  },
];

/**
 * `search` and `c/{categoryCode}` in one route, so that going from one to the other (a category
 * chosen in the filters) keeps the same page: its results stay on screen while the next ones
 * load, and the focus stays on the button that opened the filters.
 */
export function resultsPaths(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length === 1 && segments[0].path === 'search') {
    return { consumed: segments };
  }
  if (segments.length === 2 && segments[0].path === 'c') {
    return { consumed: segments, posParams: { categoryCode: segments[1] } };
  }
  return null;
}
