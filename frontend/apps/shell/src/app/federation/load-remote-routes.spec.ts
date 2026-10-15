import { TestBed } from '@angular/core/testing';
import { Routes } from '@angular/router';
import { RemoteError } from '../remote-error/remote-error';
import { loadRemoteRoutes } from './load-remote-routes';
import { RemoteModuleLoader } from './remote-module-loader';

describe('loadRemoteRoutes', () => {
  const remoteRoutes: Routes = [{ path: '', component: RemoteError }];
  let load: ReturnType<typeof vi.fn>;

  function run(remoteName: 'mfe-catalog' | 'mfe-publish'): Promise<Routes> {
    return TestBed.runInInjectionContext(() => loadRemoteRoutes(remoteName)());
  }

  beforeEach(() => {
    load = vi.fn();
    TestBed.configureTestingModule({
      providers: [{ provide: RemoteModuleLoader, useValue: { load } }],
    });
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('returns the routes exposed as "./routes"', async () => {
    load.mockResolvedValue({ routes: remoteRoutes });

    await expect(run('mfe-catalog')).resolves.toBe(remoteRoutes);
    expect(load).toHaveBeenCalledWith('mfe-catalog', './routes');
  });

  it('falls back to the error page and logs the remote name when loading fails', async () => {
    load.mockRejectedValue(new Error('Remote is down'));

    const routes = await run('mfe-publish');

    expect(routes).toEqual([
      expect.objectContaining({
        path: '**',
        component: RemoteError,
        data: { remoteName: 'mfe-publish' },
      }),
    ]);
    expect(console.error).toHaveBeenCalledWith(
      '[shell] Remote "mfe-publish" could not be loaded',
      expect.any(Error),
    );
  });

  it('falls back to the error page when the module exports no routes', async () => {
    load.mockResolvedValue({});

    const routes = await run('mfe-catalog');

    expect(routes[0]).toEqual(expect.objectContaining({ path: '**', component: RemoteError }));
  });

  it('falls back to the error page when the remote does not answer in time', async () => {
    vi.useFakeTimers();
    load.mockReturnValue(new Promise(() => undefined));

    const pending = run('mfe-catalog');
    await vi.advanceTimersByTimeAsync(10_000);

    await expect(pending).resolves.toEqual([
      expect.objectContaining({ path: '**', component: RemoteError }),
    ]);
  });
});
