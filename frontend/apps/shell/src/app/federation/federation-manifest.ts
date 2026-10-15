/** Served next to the shell (apps/shell/public). Each environment ships its own copy (DEP). */
export const FEDERATION_MANIFEST_URL = '/federation.manifest.json';

/** Names of the remotes, as declared in their federation.config.mjs and in the manifest. */
export type RemoteName = 'mfe-account' | 'mfe-catalog' | 'mfe-publish';

/** Reads the manifest again, bypassing the HTTP cache, and returns the entry URL of a remote. */
export async function readRemoteEntryUrl(
  remoteName: RemoteName,
  fetchFn: typeof fetch = fetch,
): Promise<string> {
  const response = await fetchFn(FEDERATION_MANIFEST_URL, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`${FEDERATION_MANIFEST_URL} answered HTTP ${response.status}`);
  }
  const manifest = (await response.json()) as Record<string, string | { url: string }>;
  const entry = manifest[remoteName];
  if (!entry) {
    throw new Error(`${FEDERATION_MANIFEST_URL} has no entry for "${remoteName}"`);
  }
  return typeof entry === 'string' ? entry : entry.url;
}
