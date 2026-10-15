import { withNativeFederation } from '@angular-architects/native-federation/config';
import { features, shared, sharedMappings, skip } from '../../federation.shared.mjs';

export default withNativeFederation({
  name: 'shell',
  shared,
  sharedMappings,
  skip,
  features,
});
