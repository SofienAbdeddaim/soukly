import {
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { LocaleService } from '@soukly/i18n';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { currentPageRequiresAuth, langOfUrl, loginUrlTree } from './auth.guard';
import { AuthService } from './auth.service';
import { API_PREFIX } from './session';

/** Set on a request replayed after a refresh, so that a second `401` never loops. */
const REPLAYED = new HttpContextToken<boolean>(() => false);

/**
 * Account endpoints that open or close a session, or need none: no bearer token, and their `401`
 * is an answer for the page (wrong password, expired session), not a reason to refresh.
 */
const SESSION_PATHS = [
  '/users/login',
  '/users/refresh',
  '/users/logout',
  '/users/register',
  '/users/verify-email',
  '/users/verify-email/resend',
  '/users/password/forgot',
  '/users/password/reset',
  '/users/email/confirm',
].map((path) => `${API_PREFIX}${path}`);

/**
 * For every call to the Soukly API (`/api/…` on the origin of the page):
 * - `withCredentials`, so the refresh cookie travels;
 * - `Authorization: Bearer <access token>` while a session is open;
 * - on a `401`, one refresh then one replay of the request; calls failing together wait for the
 *   same refresh. When the refresh fails, the session is cleared and a page that requires a
 *   session sends the visitor to the login page.
 * Calls to other origins are left untouched: the token never leaves the Soukly API.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApiCall(request)) {
    return next(request);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  const locale = inject(LocaleService);
  const sessionCall = SESSION_PATHS.includes(pathOf(request.url));
  const sentToken = sessionCall ? null : auth.accessToken();

  const withSession = (token: string | null, replayed = false) =>
    request.clone({
      withCredentials: true,
      setHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      context: replayed ? request.context.set(REPLAYED, true) : request.context,
    });

  return next(withSession(sentToken)).pipe(
    catchError((error: unknown) => {
      if (
        sessionCall ||
        request.context.get(REPLAYED) ||
        !(error instanceof HttpErrorResponse) ||
        error.status !== 401
      ) {
        return throwError(() => error);
      }
      // Another call already renewed the token while this one was in flight: just replay it.
      const current = auth.accessToken();
      const renewed = current && current !== sentToken ? Promise.resolve(true) : auth.refresh();
      return from(renewed).pipe(
        switchMap((ok) => {
          if (!ok) {
            if (currentPageRequiresAuth(router)) {
              const lang = langOfUrl(router, router.url, locale);
              void router.navigateByUrl(loginUrlTree(router, lang, router.url));
            }
            return throwError(() => error);
          }
          return next(withSession(auth.accessToken(), true));
        }),
      );
    }),
  );
};

function isApiCall(request: HttpRequest<unknown>): boolean {
  const path = pathOf(request.url);
  return path === API_PREFIX || path.startsWith(`${API_PREFIX}/`);
}

/** Path of a relative URL ("/api/users/login?x" -> "/api/users/login"); absolute URLs never match. */
function pathOf(url: string): string {
  return url.startsWith('/') && !url.startsWith('//') ? url.split(/[?#]/)[0] : '';
}
