import { UrlSegment } from '@angular/router';
import { resultsPaths } from './routes';

const segments = (...paths: string[]) => paths.map((path) => new UrlSegment(path, {}));

describe('resultsPaths', () => {
  it('matches /search and /c/{category} with the same route', () => {
    expect(resultsPaths(segments('search'))?.posParams).toBeUndefined();
    expect(resultsPaths(segments('c', 'vehicles.cars'))?.posParams?.['categoryCode'].path).toBe(
      'vehicles.cars',
    );
  });

  it('matches nothing else', () => {
    expect(resultsPaths(segments('c'))).toBeNull();
    expect(resultsPaths(segments('search', 'more'))).toBeNull();
    expect(resultsPaths(segments('listings', '1'))).toBeNull();
  });
});
