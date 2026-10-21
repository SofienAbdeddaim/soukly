import { UrlSegment } from '@angular/router';
import { catalogPaths } from './app.routes';

const segments = (...paths: string[]) => paths.map((path) => new UrlSegment(path, {}));

describe('catalogPaths', () => {
  it('sends the home page and the results to mfe-catalog, consuming nothing', () => {
    expect(catalogPaths(segments())).toEqual({ consumed: [] });
    expect(catalogPaths(segments('search'))).toEqual({ consumed: [] });
    expect(catalogPaths(segments('c', 'vehicles.cars'))).toEqual({ consumed: [] });
    expect(catalogPaths(segments('listings', 'abc'))).toEqual({ consumed: [] });
  });

  it('leaves the other pages to their own routes', () => {
    expect(catalogPaths(segments('account', 'login'))).toBeNull();
    expect(catalogPaths(segments('publish', 'new'))).toBeNull();
    expect(catalogPaths(segments('searching'))).toBeNull();
  });
});
