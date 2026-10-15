import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import type { NativeFederationResult } from '@angular-architects/native-federation';
import { acceptLanguageInterceptor, AlternateLinks, provideI18n } from '@soukly/i18n';
import { createShellRoutes } from './app.routes';
import { SHELL_ROUTES } from './shell-routes';
import { FEDERATION } from './federation/remote-module-loader';

export function createAppConfig(federation: NativeFederationResult | null): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideRouter(createShellRoutes(), withComponentInputBinding()),
      provideHttpClient(withFetch(), withInterceptors([acceptLanguageInterceptor])),
      provideI18n(),
      provideAppInitializer(() => inject(AlternateLinks).start()),
      { provide: SHELL_ROUTES, useValue: createShellRoutes },
      { provide: FEDERATION, useValue: federation },
    ],
  };
}
