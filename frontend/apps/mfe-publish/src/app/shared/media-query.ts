import { DestroyRef, inject, Signal, signal } from '@angular/core';

/**
 * Whether a CSS media query matches, kept up to date while the calling component lives. False
 * where `matchMedia` does not exist (unit tests), so the narrow layout is the default.
 */
export function mediaQuery(query: string): Signal<boolean> {
  const list = typeof matchMedia === 'function' ? matchMedia(query) : null;
  const matches = signal(list?.matches ?? false);
  if (list) {
    const listener = (event: MediaQueryListEvent) => matches.set(event.matches);
    list.addEventListener('change', listener);
    inject(DestroyRef).onDestroy(() => list.removeEventListener('change', listener));
  }
  return matches.asReadonly();
}
