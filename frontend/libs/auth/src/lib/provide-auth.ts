import { EnvironmentProviders, inject, provideAppInitializer } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Longest wait for the restored session before the first render. A slower answer still completes
 * the session later; pages behind `authGuard` always wait for it.
 */
export const SESSION_RESTORE_WAIT_MS = 3000;

/**
 * Restores the session of a previous visit when the app starts (silent `refresh` with the
 * HttpClient of the app, which must use `authInterceptor`), so that the header shows the member at
 * first paint.
 */
export function provideAuth(): EnvironmentProviders {
  return provideAppInitializer(() => {
    const restored = inject(AuthService).restore();
    return Promise.race([
      restored,
      new Promise<void>((resolve) => setTimeout(resolve, SESSION_RESTORE_WAIT_MS)),
    ]);
  });
}
