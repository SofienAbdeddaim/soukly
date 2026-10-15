import { readRemoteEntryUrl } from './federation-manifest';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

describe('readRemoteEntryUrl', () => {
  it('returns the entry URL of a remote, read without the HTTP cache', async () => {
    const fetchFn = vi
      .fn()
      .mockResolvedValue(jsonResponse({ 'mfe-catalog': 'http://localhost:4202/remoteEntry.json' }));

    await expect(readRemoteEntryUrl('mfe-catalog', fetchFn)).resolves.toBe(
      'http://localhost:4202/remoteEntry.json',
    );
    expect(fetchFn).toHaveBeenCalledWith('/federation.manifest.json', { cache: 'no-store' });
  });

  it('accepts the object form of an entry', async () => {
    const fetchFn = vi
      .fn()
      .mockResolvedValue(jsonResponse({ 'mfe-account': { url: 'https://account.test/r.json' } }));

    await expect(readRemoteEntryUrl('mfe-account', fetchFn)).resolves.toBe(
      'https://account.test/r.json',
    );
  });

  it('fails when the manifest is not served', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response('', { status: 404 }));

    await expect(readRemoteEntryUrl('mfe-publish', fetchFn)).rejects.toThrow('HTTP 404');
  });

  it('fails when the manifest has no entry for the remote', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({}));

    await expect(readRemoteEntryUrl('mfe-publish', fetchFn)).rejects.toThrow(
      'no entry for "mfe-publish"',
    );
  });
});
