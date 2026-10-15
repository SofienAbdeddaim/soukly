import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideI18n } from '@soukly/i18n';
import { appRoutes } from './app.routes';

/** Standalone launch: no shell, so no root translations, only the scope of this remote. */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(appRoutes),
    provideI18n({ rootTranslations: false }),
  ],
};
