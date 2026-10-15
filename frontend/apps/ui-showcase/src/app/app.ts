import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  computed,
  inject,
  signal,
} from '@angular/core';
import { LucideArrowRight, LucideCamera } from '@lucide/angular';
import {
  ToastService,
  UiButton,
  UiField,
  UiFieldControl,
  UiListingCard,
  UiSkeleton,
  UiToastType,
} from '@soukly/ui';
import { LABELS, ShowcaseLang } from './labels';

/**
 * Single page that shows every component of libs/ui in all its states. The toggle switches
 * `dir` and `lang` on <html> (ltr/fr <-> rtl/ar). Development tool only, never deployed.
 */
@Component({
  selector: 'app-root',
  imports: [
    LucideArrowRight,
    LucideCamera,
    UiButton,
    UiField,
    UiFieldControl,
    UiListingCard,
    UiSkeleton,
  ],
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly document = inject(DOCUMENT);
  private readonly toasts = inject(ToastService);

  protected readonly lang = signal<ShowcaseLang>('fr');
  protected readonly t = computed(() => LABELS[this.lang()]);
  protected readonly favorites = signal<boolean[]>([false, true, false]);
  protected readonly imageUrl = 'sample-listing.webp';

  protected toggleDirection(): void {
    const lang: ShowcaseLang = this.lang() === 'fr' ? 'ar' : 'fr';
    this.lang.set(lang);
    const html = this.document.documentElement;
    html.lang = lang;
    html.dir = lang === 'ar' ? 'rtl' : 'ltr';
  }

  protected setFavorite(index: number, value: boolean): void {
    this.favorites.update((favorites) => favorites.map((f, i) => (i === index ? value : f)));
  }

  protected showToast(type: UiToastType): void {
    const t = this.t();
    const message = { info: t.infoMessage, success: t.successMessage, error: t.errorMessage }[type];
    this.toasts.show({ type, message, closeLabel: t.close });
  }
}
