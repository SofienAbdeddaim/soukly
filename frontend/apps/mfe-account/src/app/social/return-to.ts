/**
 * The preview of a draft that sent the member to "My networks" (SOC-03), kept in the session
 * storage of the tab while the member connects on Facebook or Instagram (the network sends them
 * back without it). Never anything but a path of the publish form of this site.
 */
export const RETURN_TO_PARAM = 'returnTo';

const STORAGE_KEY = 'soukly.social.returnTo';
const PUBLISH_PATH = /^\/(fr|ar|en)\/publish\/[0-9a-f-]{36}\/preview$/;

export function isPublishPath(path: string): boolean {
  return PUBLISH_PATH.test(path);
}

export function rememberReturnTo(path: string): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, path);
  } catch {
    // Storage blocked: the member goes back by the menu
  }
}

export function recallReturnTo(): string | null {
  try {
    const path = sessionStorage.getItem(STORAGE_KEY);
    return path && isPublishPath(path) ? path : null;
  } catch {
    return null;
  }
}

export function forgetReturnTo(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing kept
  }
}
