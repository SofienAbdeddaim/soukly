import { inject, Injectable } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { Lang } from './languages';
import { LocaleService } from './locale.service';

/**
 * Switches the page to a language: the root translations are downloaded first, then `<html>`
 * (lang, dir) and Transloco change in the same tick, so the page never shows a text in the
 * wrong direction.
 */
@Injectable({ providedIn: 'root' })
export class LangActivation {
  private readonly transloco = inject(TranslocoService);
  private readonly locale = inject(LocaleService);

  async activate(lang: Lang): Promise<void> {
    try {
      await firstValueFrom(this.transloco.load(lang));
    } catch (error) {
      // The texts fall back to French (or to their keys): the page stays usable.
      console.error(`[i18n] Translations "${lang}" could not be loaded`, error);
    }
    this.locale.use(lang);
    this.transloco.setActiveLang(lang);
  }
}
