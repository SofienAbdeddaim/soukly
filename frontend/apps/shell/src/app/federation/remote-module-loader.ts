import { inject, Injectable, InjectionToken } from '@angular/core';
import { initFederation, type NativeFederationResult } from '@angular-architects/native-federation';
import { readRemoteEntryUrl, type RemoteName } from './federation-manifest';

/** Result of initFederation in main.ts, or null when the manifest could not be read. */
export const FEDERATION = new InjectionToken<NativeFederationResult | null>('FEDERATION', {
  factory: () => null,
});

/**
 * Loads an exposed module of a remote. If the first attempt fails (remote stopped when the shell
 * started, manifest unreadable, remote restarted since), the manifest is read again and the remote
 * entry registered again before a second attempt, so "Réessayer" works without reloading the page.
 */
@Injectable({ providedIn: 'root' })
export class RemoteModuleLoader {
  private federation = inject(FEDERATION);

  async load<T>(remoteName: RemoteName, exposedModule: string): Promise<T> {
    if (this.federation) {
      try {
        return await this.federation.loadRemoteModule<T>(remoteName, exposedModule);
      } catch {
        // Second attempt below, with the remote entry registered again.
      }
    }
    const remoteEntryUrl = await readRemoteEntryUrl(remoteName);
    this.federation = this.federation
      ? await this.federation.initRemoteEntry(remoteEntryUrl, remoteName)
      : await initFederation({ [remoteName]: remoteEntryUrl });
    return this.federation.loadRemoteModule<T>(remoteName, exposedModule);
  }
}
