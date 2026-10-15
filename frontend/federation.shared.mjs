import { fromPackageJson } from '@angular-architects/native-federation/config';

/**
 * Dependencies shared by the shell and every remote (SETUP-05), so the browser downloads them
 * once. Every npm dependency of package.json (@angular/*, rxjs, @angular/cdk, @lucide/angular…)
 * and the workspace libraries mapped in tsconfig.base.json (@soukly/*, today @soukly/ui) are singletons with a
 * strict version: a remote built against another version fails loudly instead of loading a
 * second copy of Angular. Only what a project actually imports is bundled (ignoreUnusedDeps).
 */
export const shared = fromPackageJson({
  singleton: true,
  strictVersion: true,
  requiredVersion: 'auto',
  build: 'package',
})
  // Keep every secondary entry point of @angular/core, so host and remotes never disagree on one.
  .patch(['@angular/core'], { includeSecondaries: { keepAll: true } });

export const sharedMappings = [[['@soukly/*'], { singleton: true, strictVersion: true }]];

/** Packages never needed at runtime. */
export const skip = ['rxjs/ajax', 'rxjs/fetch', 'rxjs/testing', 'rxjs/webSocket'];

export const features = { denseChunking: true };
