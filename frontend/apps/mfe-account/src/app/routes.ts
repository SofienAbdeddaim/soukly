import { Routes } from '@angular/router';
import { provideI18nScope } from '@soukly/i18n';
import { AccountComingSoon } from './coming-soon/account-coming-soon';

/**
 * Routes exposed to the shell as "./routes" (federation.config.mjs), relative to the path the
 * shell mounts them on. The wrapping route provides the "account" translations, downloaded from this
 * remote (public/i18n/account) when one of its pages is shown.
 */
export const routes: Routes = [
  {
    path: '',
    providers: [provideI18nScope('account', import.meta.url)],
    children: [{ path: '**', component: AccountComingSoon }],
  },
];
