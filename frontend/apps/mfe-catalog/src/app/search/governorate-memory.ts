import { DOCUMENT, inject, Injectable, signal } from '@angular/core';

/** localStorage key of the last governorate chosen in a filter (CATA-02). */
export const GOVERNORATE_STORAGE_KEY = 'soukly.governorate';

/** A governorate code as CAT-01 writes them: lower case letters and dashes. */
const GOVERNORATE_CODE = /^[a-z][a-z-]{1,40}$/u;

/**
 * Last governorate the visitor chose in a filter, kept in the browser for the next visits and
 * applied to the home page and to new searches; "All of Tunisia" forgets it. Never the location
 * of the browser. The storage may be disabled, full or hold anything: every access is guarded,
 * and the choice then only lasts for the page.
 */
@Injectable({ providedIn: 'root' })
export class GovernorateMemory {
  private readonly storage = storageOf(inject(DOCUMENT));
  private readonly value = signal<string | null>(this.read());

  readonly governorate = this.value.asReadonly();

  remember(code: string | null): void {
    this.value.set(code);
    try {
      if (code) {
        this.storage?.setItem(GOVERNORATE_STORAGE_KEY, code);
      } else {
        this.storage?.removeItem(GOVERNORATE_STORAGE_KEY);
      }
    } catch {
      // Private browsing or storage disabled: the choice only lasts for this page.
    }
  }

  private read(): string | null {
    try {
      const code = this.storage?.getItem(GOVERNORATE_STORAGE_KEY) ?? null;
      return code && GOVERNORATE_CODE.test(code) ? code : null;
    } catch {
      return null;
    }
  }
}

function storageOf(document: Document): Storage | undefined {
  try {
    return document.defaultView?.localStorage;
  } catch {
    return undefined;
  }
}
