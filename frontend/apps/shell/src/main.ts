import { initFederation } from '@angular-architects/native-federation';
import { FEDERATION_MANIFEST_URL } from './app/federation/federation-manifest';

// The manifest is read before Angular starts: it gives the URL of every remote, so a remote can
// move without rebuilding the shell. If it cannot be read, the shell still starts and shows the
// "remote unavailable" page in the content area.
// hostRemoteEntry registers the shared dependencies of the shell itself (Angular, rxjs, @soukly/ui).
initFederation(FEDERATION_MANIFEST_URL, { hostRemoteEntry: { url: './remoteEntry.json' } })
  .catch((error: unknown) => {
    console.error(
      `[shell] The federation manifest ${FEDERATION_MANIFEST_URL} could not be read`,
      error,
    );
    return null;
  })
  .then((federation) => import('./bootstrap').then(({ bootstrap }) => bootstrap(federation)))
  .catch((error: unknown) => console.error(error));
