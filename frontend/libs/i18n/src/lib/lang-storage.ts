import { isLang, Lang } from './languages';

/** localStorage key of the language chosen in the language switcher. */
export const LANG_STORAGE_KEY = 'soukly.lang';

/** Language chosen earlier, or null. Storage may be disabled or full: errors are ignored. */
export function readStoredLang(
  storage: Storage | undefined = globalThis.localStorage,
): Lang | null {
  try {
    const value = storage?.getItem(LANG_STORAGE_KEY);
    return isLang(value) ? value : null;
  } catch {
    return null;
  }
}

export function storeLang(
  lang: Lang,
  storage: Storage | undefined = globalThis.localStorage,
): void {
  try {
    storage?.setItem(LANG_STORAGE_KEY, lang);
  } catch {
    // Private browsing or storage disabled: the choice only lasts for this page.
  }
}
