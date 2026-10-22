import { DOCUMENT, inject } from '@angular/core';
import { Router } from '@angular/router';

/**
 * The email address given to a page in the navigation state (by the registration, login or
 * forgotten password page), kept by the browser history on reload; never read from the URL. Call
 * it in an injection context. Null when none was given.
 */
export function navigationEmail(): string | null {
  const state =
    inject(Router).currentNavigation()?.extras.state ?? inject(DOCUMENT).defaultView?.history.state;
  const email =
    state !== null && typeof state === 'object'
      ? (state as Record<string, unknown>)['email']
      : null;
  return typeof email === 'string' && email ? email : null;
}
