import { initFederation } from '@angular-architects/native-federation';

// Standalone launch: the remote registers its own shared dependencies (remoteEntry.json), then
// starts Angular. Inside the shell this file is not used, the shell loads "./routes" only.
initFederation({}, { hostRemoteEntry: { url: './remoteEntry.json' } })
  .catch((error: unknown) => console.error(error))
  .then(() => import('./bootstrap'))
  .catch((error: unknown) => console.error(error));
