import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { authInterceptor, provideAuth } from '@soukly/auth';
import { acceptLanguageInterceptor, provideI18n } from '@soukly/i18n';
import { appRoutes } from './app.routes';

/**
 * Standalone launch: no shell, so no root translations, only the scope of this remote. The HTTP
 * client and the session are set up as in the shell.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(appRoutes, withComponentInputBinding()),
    provideI18n({ rootTranslations: false }),
    provideHttpClient(withFetch(), withInterceptors([acceptLanguageInterceptor, authInterceptor])),
    provideAuth(),
  ],
};
