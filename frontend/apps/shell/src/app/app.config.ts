import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import type { NativeFederationResult } from '@angular-architects/native-federation';
import { createShellRoutes } from './app.routes';
import { SHELL_ROUTES } from './shell-routes';
import { FEDERATION } from './federation/remote-module-loader';

export function createAppConfig(federation: NativeFederationResult | null): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideRouter(createShellRoutes(), withComponentInputBinding()),
      { provide: SHELL_ROUTES, useValue: createShellRoutes },
      { provide: FEDERATION, useValue: federation },
    ],
  };
}
