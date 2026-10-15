import { withNativeFederation } from '@angular-architects/native-federation/config';
import { features, shared, sharedMappings, skip } from '../../federation.shared.mjs';

export default withNativeFederation({
  name: 'mfe-account',
  exposes: {
    './routes': './apps/mfe-account/src/app/routes.ts',
  },
  shared,
  sharedMappings,
  skip,
  features,
});
