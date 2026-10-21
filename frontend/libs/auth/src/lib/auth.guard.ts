import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, UrlTree } from '@angular/router';
import { isLang, Lang, LocaleService } from '@soukly/i18n';
import { AuthService } from './auth.service';
import { safeReturnUrl } from './return-url';

/**
 * Pages for signed-in members only (`mfe-publish`, welcome screen…): waits for the session restored
 * at start-up, then lets a member in or sends a visitor to the login page of the same language,
 * with the requested page as `returnUrl` (`/fr/account/login?returnUrl=%2Ffr%2Fpublish%2Fnew`).
 */
export const authGuard: CanActivateFn = async (_route, state) => {
  // Everything is injected before the first await: inject() only works synchronously.
  const auth = inject(AuthService);
  const router = inject(Router);
  const locale = inject(LocaleService);
  await auth.whenRestored();
  if (auth.isAuthenticated()) {
    return true;
  }
  return loginUrlTree(router, langOfUrl(router, state.url, locale), state.url);
};

/**
 * Login and registration pages: a signed-in member goes straight to the `returnUrl` of the page,
 * or to the home page.
 */
export const guestGuard: CanActivateFn = async (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const locale = inject(LocaleService);
  await auth.whenRestored();
  if (!auth.isAuthenticated()) {
    return true;
  }
  const lang = langOfUrl(router, state.url, locale);
  return router.parseUrl(safeReturnUrl(route.queryParamMap.get('returnUrl')) ?? `/${lang}`);
};

/** Login page of a language, with an optional page to come back to. */
export function loginUrlTree(router: Router, lang: Lang, returnUrl?: string): UrlTree {
  const safe = safeReturnUrl(returnUrl);
  return router.createUrlTree(['/', lang, 'account', 'login'], {
    queryParams: safe ? { returnUrl: safe } : {},
  });
}

/** True when a route of the current page is protected by `authGuard`. */
export function currentPageRequiresAuth(router: Router): boolean {
  let route: ActivatedRouteSnapshot | null = router.routerState.snapshot.root;
  while (route) {
    if (route.routeConfig?.canActivate?.includes(authGuard)) {
      return true;
    }
    route = route.firstChild;
  }
  return false;
}

/** Language prefix of a URL, else the current language. */
export function langOfUrl(router: Router, url: string, locale: LocaleService): Lang {
  const first = router.parseUrl(url).root.children['primary']?.segments[0]?.path;
  return isLang(first) ? first : locale.lang();
}
