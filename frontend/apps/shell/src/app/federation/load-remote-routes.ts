import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { RemoteError } from '../remote-error/remote-error';
import type { RemoteName } from './federation-manifest';
import { RemoteModuleLoader } from './remote-module-loader';

/** A remote that does not answer within this delay is reported as unavailable. */
export const REMOTE_LOAD_TIMEOUT_MS = 10_000;

/** Module exposed by every remote as "./routes". */
export interface RemoteRoutesModule {
  routes: Routes;
}

/**
 * `loadChildren` of a shell route: loads the routes exposed by a remote. On failure, the error is
 * logged with the remote name and the content area shows the error page, header and footer stay.
 */
export function loadRemoteRoutes(remoteName: RemoteName): () => Promise<Routes> {
  return async () => {
    const loader = inject(RemoteModuleLoader);
    try {
      const module = await withTimeout(
        loader.load<Partial<RemoteRoutesModule>>(remoteName, './routes'),
        REMOTE_LOAD_TIMEOUT_MS,
      );
      if (!Array.isArray(module?.routes)) {
        throw new Error(`"./routes" does not export a "routes" array`);
      }
      return module.routes;
    } catch (error) {
      console.error(`[shell] Remote "${remoteName}" could not be loaded`, error);
      return [{ path: '**', component: RemoteError, data: { remoteName } }];
    }
  };
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(`No answer after ${timeoutMs} ms`)), timeoutMs);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}
