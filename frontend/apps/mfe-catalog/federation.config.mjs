import { withNativeFederation } from '@angular-architects/native-federation/config';
import { features, shared, sharedMappings, skip } from '../../federation.shared.mjs';

export default withNativeFederation({
  name: 'mfe-catalog',
  exposes: {
    './routes': './apps/mfe-catalog/src/app/routes.ts',
  },
  shared,
  sharedMappings,
  skip,
  features,
});
