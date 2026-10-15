import { DestroyRef, inject, Injectable } from '@angular/core';
import { TranslocoEvents, TranslocoService } from '@jsverse/transloco';
import { filter } from 'rxjs';
import { DEFAULT_LANG } from './languages';

/**
 * Development only: warns in the console about every key of a translation file that exists in
 * French but not in the language just loaded. At runtime Transloco shows the French text for such
 * a key (fallback translation), so the gap would otherwise go unnoticed. CI catches the same gaps
 * with `i18n-check`.
 */
@Injectable({ providedIn: 'root' })
export class MissingTranslationReporter {
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  /** "<scope>|<lang>" of every loaded file; the root scope is "". */
  private readonly loaded = new Set<string>();
  private readonly reported = new Set<string>();

  start(): void {
    const subscription = this.transloco.events$
      .pipe(
        filter(
          (event): event is Extract<TranslocoEvents, { type: 'translationLoadSuccess' }> =>
            event.type === 'translationLoadSuccess',
        ),
      )
      .subscribe(({ payload }) => this.onLoaded(payload.scope ?? '', payload.langName));
    this.destroyRef.onDestroy(() => subscription.unsubscribe());
  }

  private onLoaded(scope: string, lang: string): void {
    this.loaded.add(`${scope}|${lang}`);
    if (!this.loaded.has(`${scope}|${DEFAULT_LANG}`)) {
      return;
    }
    // Compare once both files of the scope are there, whatever order they arrived in.
    for (const entry of this.loaded) {
      const [loadedScope, loadedLang] = entry.split('|');
      if (loadedScope === scope && loadedLang !== DEFAULT_LANG) {
        this.report(scope, loadedLang);
      }
    }
  }

  private report(scope: string, lang: string): void {
    const present = new Set(this.keys(scope, lang));
    for (const key of this.keys(scope, DEFAULT_LANG)) {
      const id = `${lang}|${key}`;
      if (!present.has(key) && !this.reported.has(id)) {
        this.reported.add(id);
        console.warn(
          `[i18n] Missing translation "${key}" in "${lang}"${scope ? ` (scope "${scope}")` : ''}: the French text is shown.`,
        );
      }
    }
  }

  /** Flat keys of one scope in a language (scoped keys start with "<scope>."). */
  private keys(scope: string, lang: string): string[] {
    const scopes = [...this.loaded]
      .map((entry) => entry.split('|')[0])
      .filter((name) => name !== '');
    return Object.keys(this.transloco.getTranslation(lang)).filter((key) =>
      scope ? key.startsWith(`${scope}.`) : !scopes.some((name) => key.startsWith(`${name}.`)),
    );
  }
}
