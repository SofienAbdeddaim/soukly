import { DestroyRef, inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { TRANSLOCO_SCOPE, TranslocoService } from '@jsverse/transloco';
import { combineLatest, Subscription } from 'rxjs';

/** Title of the shell (`index.html`). */
export const SHELL_TITLE = 'Soukly';

/**
 * `<title>` and `<meta name="description">` of a catalogue page (basic SEO, no SSR in V1), in the
 * language of the page. Keys of the `catalog` scope, without its prefix: the texts are read once the
 * scope of the remote is loaded. The hreflang links are kept by the shell (SETUP-07).
 * Provided by each page, so a page that is left stops updating them.
 */
@Injectable()
export class PageMeta {
  private readonly transloco = inject(TranslocoService);
  private readonly scope = inject(TRANSLOCO_SCOPE, { optional: true });
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private subscription?: Subscription;

  constructor() {
    // The pages of the other remotes set no title yet: they get back the one of the shell.
    inject(DestroyRef).onDestroy(() => {
      this.subscription?.unsubscribe();
      this.title.setTitle(SHELL_TITLE);
      this.meta.removeTag('name="description"');
    });
  }

  set(titleKey: string, descriptionKey: string, params: Record<string, string> = {}): void {
    this.subscription?.unsubscribe();
    this.subscription = combineLatest([
      this.transloco.selectTranslate<string>(titleKey, params, this.scope ?? 'catalog'),
      this.transloco.selectTranslate<string>(descriptionKey, params, this.scope ?? 'catalog'),
    ]).subscribe(([title, description]) => {
      this.title.setTitle(title);
      this.meta.updateTag({ name: 'description', content: description });
    });
  }
}
