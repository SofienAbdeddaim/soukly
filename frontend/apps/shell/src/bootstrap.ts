import { bootstrapApplication } from '@angular/platform-browser';
import type { NativeFederationResult } from '@angular-architects/native-federation';
import { App } from './app/app';
import { createAppConfig } from './app/app.config';

export function bootstrap(federation: NativeFederationResult | null) {
  return bootstrapApplication(App, createAppConfig(federation));
}
