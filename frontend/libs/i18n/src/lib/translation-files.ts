import { Translation } from '@jsverse/transloco';

/** Downloads a JSON translation file. */
export async function fetchTranslation(
  url: string | URL,
  fetchFn: typeof fetch = fetch,
): Promise<Translation> {
  const response = await fetchFn(url);
  if (!response.ok) {
    throw new Error(`${url} answered HTTP ${response.status}`);
  }
  return (await response.json()) as Translation;
}
