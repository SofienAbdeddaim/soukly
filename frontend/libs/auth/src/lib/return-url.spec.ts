import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('keeps an internal path with its query and fragment', () => {
    expect(safeReturnUrl('/fr/publish/new')).toBe('/fr/publish/new');
    expect(safeReturnUrl('/ar/search?q=velo#top')).toBe('/ar/search?q=velo#top');
  });

  it.each([
    'https://evil.example/fr',
    '//evil.example/fr',
    '/fr//evil.example',
    '/\\evil.example',
    'javascript:alert(1)',
    'fr/publish/new',
    '/fr/\npublish',
    '',
    null,
    undefined,
    42,
  ])('refuses %j', (value) => {
    expect(safeReturnUrl(value)).toBeNull();
  });

  it('refuses a very long value', () => {
    expect(safeReturnUrl(`/${'a'.repeat(2000)}`)).toBeNull();
  });
});
