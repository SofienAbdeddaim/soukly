import { Routes } from '@angular/router';
import { authGuard } from '@soukly/auth';
import { provideI18nScope } from '@soukly/i18n';
import { CategoryStep } from './category/category-step';
import { NewListingPage } from './category/new-listing-page';
import { DetailsStep } from './details/details-step';
import { DraftPage } from './draft/draft-page';
import { ResumeStep } from './draft/resume-step';
import { DonePage } from './done/done-page';
import { PhotosStep } from './photos/photos-step';
import { PreviewStep } from './preview/preview-step';
import { PriceLocationStep } from './price-location/price-location-step';

/**
 * Routes exposed to the shell as "./routes" (federation.config.mjs), relative to the path the
 * shell mounts them on (`/{lang}/publish`). The wrapping route provides the "publish" translations,
 * downloaded from this remote (public/i18n/publish) when one of its pages is shown. Every page
 * needs a session: a visitor is sent to the login page, then brought back (authGuard).
 *
 * - `new`: step 1 without a draft; the draft is created when the subcategory is chosen;
 * - `{id}`: the first incomplete step of the draft (resumed from "My listings", ANN-05);
 * - `{id}/{step}`: a step of the draft, read again from the API when the URL is reloaded;
 * - `{id}/done`: confirmation after the publication.
 */
export const routes: Routes = [
  {
    path: '',
    providers: [provideI18nScope('publish', import.meta.url)],
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'new' },
      { path: 'new', component: NewListingPage },
      // Before `:id`: once published, the listing is no longer a draft the draft page can read.
      { path: ':id/done', component: DonePage },
      {
        path: ':id',
        component: DraftPage,
        children: [
          { path: '', pathMatch: 'full', component: ResumeStep },
          { path: 'category', component: CategoryStep },
          { path: 'photos', component: PhotosStep },
          { path: 'details', component: DetailsStep },
          { path: 'price-location', component: PriceLocationStep },
          { path: 'preview', component: PreviewStep },
          { path: '**', redirectTo: '' },
        ],
      },
    ],
  },
];
