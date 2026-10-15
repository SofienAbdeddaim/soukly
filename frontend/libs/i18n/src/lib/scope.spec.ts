import { TestBed } from '@angular/core/testing';
import { TRANSLOCO_SCOPE } from '@jsverse/transloco';
import { provideI18nScope, scopeFileUrl } from './scope';

describe('provideI18nScope', () => {
  afterEach(() => vi.restoreAllMocks());

  it('resolves the files next to the module of the remote, not on the shell', () => {
    expect(scopeFileUrl('publish', 'ar', 'http://localhost:4203/chunk-ABC.js').href).toBe(
      'http://localhost:4203/i18n/publish/ar.json',
    );
    expect(scopeFileUrl('catalog', 'fr', 'https://cdn.example.test/mfe-catalog/main.js').href).toBe(
      'https://cdn.example.test/mfe-catalog/i18n/catalog/fr.json',
    );
  });

  it('declares the scope with one loader per language, which downloads only its file', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ title: 'عنوان' })));
    TestBed.configureTestingModule({
      providers: [provideI18nScope('publish', 'http://localhost:4203/routes.js')],
    });
    const [scope] = TestBed.inject(TRANSLOCO_SCOPE) as unknown as {
      scope: string;
      loader: Record<string, () => Promise<unknown>>;
    }[];

    expect(scope.scope).toBe('publish');
    expect(Object.keys(scope.loader)).toEqual(['fr', 'ar', 'en']);
    expect(fetchSpy).not.toHaveBeenCalled();

    await expect(scope.loader['ar']()).resolves.toEqual({ title: 'عنوان' });
    expect(fetchSpy).toHaveBeenCalledWith(new URL('http://localhost:4203/i18n/publish/ar.json'));
  });

  it('fails when the file is not served', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 404 }));
    TestBed.configureTestingModule({
      providers: [provideI18nScope('publish', 'http://localhost:4203/routes.js')],
    });
    const [scope] = TestBed.inject(TRANSLOCO_SCOPE) as unknown as {
      loader: Record<string, () => Promise<unknown>>;
    }[];

    await expect(scope.loader['fr']()).rejects.toThrow('HTTP 404');
  });
});
