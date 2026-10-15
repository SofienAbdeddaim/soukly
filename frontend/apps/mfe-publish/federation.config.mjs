import { withNativeFederation } from '@angular-architects/native-federation/config';
import { features, shared, sharedMappings, skip } from '../../federation.shared.mjs';

export default withNativeFederation({
  name: 'mfe-publish',
  exposes: {
    './routes': './apps/mfe-publish/src/app/routes.ts',
  },
  shared,
  sharedMappings,
  skip,
  features,
});
