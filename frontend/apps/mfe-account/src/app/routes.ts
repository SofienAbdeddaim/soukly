import { Routes } from '@angular/router';
import { authGuard, guestGuard } from '@soukly/auth';
import { provideI18nScope } from '@soukly/i18n';
import { CheckEmailPage } from './check-email/check-email-page';
import { AccountComingSoon } from './coming-soon/account-coming-soon';
import { ForgotPasswordPage } from './forgot-password/forgot-password-page';
import { ResetLinkSentPage } from './forgot-password/reset-link-sent-page';
import { MyListingsPage } from './listings/my-listings-page';
import { LoginPage } from './login/login-page';
import { RegisterPage } from './register/register-page';
import { ResetPasswordPage } from './reset-password/reset-password-page';
import { VerifyEmailPage } from './verify-email/verify-email-page';
import { WelcomePage } from './welcome/welcome-page';

/**
 * Routes exposed to the shell as "./routes" (federation.config.mjs), relative to the path the
 * shell mounts them on. The wrapping route provides the "account" translations, downloaded from this
 * remote (public/i18n/account) when one of its pages is shown.
 */
export const routes: Routes = [
  {
    path: '',
    providers: [provideI18nScope('account', import.meta.url)],
    children: [
      // Profile: AUTH-05.
      { path: '', pathMatch: 'full', component: AccountComingSoon, canActivate: [authGuard] },
      { path: 'register', component: RegisterPage, canActivate: [guestGuard] },
      { path: 'check-email', component: CheckEmailPage },
      { path: 'verify-email', component: VerifyEmailPage },
      { path: 'login', component: LoginPage, canActivate: [guestGuard] },
      { path: 'welcome', component: WelcomePage, canActivate: [authGuard] },
      // My listings (ANN-05), `?tab=online|drafts|expired|inactive`.
      { path: 'listings', component: MyListingsPage, canActivate: [authGuard] },
      // Forgotten password (AUTH-04). Open to a signed-in member too: a reset link can come from
      // another device.
      { path: 'forgot-password', component: ForgotPasswordPage },
      { path: 'forgot-password/sent', component: ResetLinkSentPage },
      { path: 'reset-password', component: ResetPasswordPage },
      // Waiting pages: Facebook and Instagram links (SOC-01, SOC-02).
      { path: 'networks/:network', component: AccountComingSoon, canActivate: [authGuard] },
      { path: '**', component: AccountComingSoon },
    ],
  },
];
