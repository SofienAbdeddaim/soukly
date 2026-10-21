import { Dialog } from '@angular/cdk/dialog';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LucideArrowLeft } from '@lucide/angular';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import { labelOf, ListingStatus, ReferentialsApi } from '@soukly/listing-data';
import { ToastService, UiAlert, UiButton, UiSkeleton } from '@soukly/ui';
import { combineLatest, firstValueFrom, take } from 'rxjs';
import { DetailsSection } from '../details/details-section';
import { DraftStore } from '../draft/draft-store';
import { PhotosSection } from '../photos/photos-section';
import { PriceLocationSection } from '../price-location/price-location-section';
import { ConfirmLeave } from './leave-guard';
import { LeaveDialog } from './leave-dialog';

/** Tab of "My listings" (mfe-account) that shows a listing of this status. */
const LIST_TAB: Partial<Record<ListingStatus, string>> = {
  PUBLISHED: 'online',
  EXPIRED: 'expired',
  INACTIVE: 'inactive',
};

/**
 * Edit page of a published, expired or deactivated listing (`/{lang}/publish/{id}/edit`, ANN-05):
 * the photos, details and price sections of the form on one page, the category read only.
 *
 * Nothing is saved while the member types: "Save" sends every change in one PATCH (a published
 * listing changes at once), then goes back to "My listings". Photos are the exception, applied as
 * soon as they are added, removed or moved (ANN-02). Leaving with changes not saved, by "Cancel",
 * a link or the browser, asks first. A draft opens its own form instead.
 */
@Component({
  selector: 'app-edit-page',
  imports: [
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideArrowLeft,
    UiAlert,
    UiButton,
    UiSkeleton,
    PhotosSection,
    DetailsSection,
    PriceLocationSection,
  ],
  providers: [DraftStore],
  templateUrl: './edit-page.html',
  styleUrls: ['./edit-page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(window:beforeunload)': 'beforeUnload($event)' },
})
export class EditPage implements ConfirmLeave {
  protected readonly store = inject(DraftStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly referentials = inject(ReferentialsApi);
  private readonly dialog = inject(Dialog);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  private readonly photos = viewChild(PhotosSection);
  private readonly details = viewChild(DetailsSection);
  private readonly place = viewChild(PriceLocationSection);

  protected readonly saving = signal(false);
  private readonly categoryLabels = signal<Record<string, string>>({});
  private saved = false;
  private id = '';

  protected readonly status = computed(() => this.store.listing()?.status ?? null);
  protected readonly category = computed(() => {
    const code = this.store.listing()?.categoryCode;
    return code ? (this.categoryLabels()[code] ?? '') : '';
  });
  protected readonly listTab = computed(() => LIST_TAB[this.status() ?? 'PUBLISHED'] ?? 'online');

  constructor() {
    const subscription = this.route.paramMap.subscribe((params) => {
      this.id = params.get('id') ?? '';
      void this.store.load(this.id, 'edit');
    });
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());

    // A draft is finished in the steps of the form (ANN-04).
    effect(() => {
      const listing = this.store.listing();
      if (listing?.status === 'DRAFT') {
        untracked(() => {
          this.saved = true;
          void this.router.navigate(['/', this.locale.lang(), 'publish', listing.id], {
            replaceUrl: true,
          });
        });
      }
    });

    effect(() => {
      if (this.store.loadState() === 'ready') {
        untracked(() =>
          afterNextRender(() => this.element.querySelector<HTMLElement>('h1')?.focus(), {
            injector: this.injector,
          }),
        );
      }
    });

    void this.loadCategories();
  }

  protected retry(): void {
    void this.store.load(this.id, 'edit');
  }

  protected reload(): void {
    void this.store.reload();
  }

  protected cancel(): void {
    void this.router.navigate(this.listCommands(), { queryParams: { tab: this.listTab() } });
  }

  protected async save(): Promise<void> {
    const photos = this.photos();
    const details = this.details();
    const place = this.place();
    if (!photos || !details || !place || this.saving()) {
      return;
    }
    const detailsValid = details.validate();
    const placeValid = place.validate();
    if (!photos.check()) {
      return;
    }
    if (!detailsValid || !placeValid) {
      (detailsValid ? place : details).focusFirstError();
      return;
    }
    details.saveChanges();
    place.saveChanges();
    this.saving.set(true);
    const tab = this.listTab();
    const ok = await this.store.commit();
    this.saving.set(false);
    if (ok) {
      this.saved = true;
      this.toast('edit.saved', 'success');
      await this.router.navigate(this.listCommands(), { queryParams: { tab } });
    } else if (this.store.saveState() === 'invalid') {
      details.focusFirstError();
      place.focusFirstError();
    }
  }

  canLeave(): boolean | Promise<boolean> {
    if (!this.hasChanges()) {
      return true;
    }
    const dialogRef = this.dialog.open<boolean, void, LeaveDialog>(LeaveDialog, {
      injector: this.injector,
      role: 'alertdialog',
      ariaLabelledBy: 'leave-dialog-title',
      ariaDescribedBy: 'leave-dialog-text',
      maxWidth: '100vw',
    });
    return firstValueFrom(dialogRef.closed).then((leave) => leave === true);
  }

  protected beforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasChanges()) {
      event.preventDefault();
    }
  }

  private hasChanges(): boolean {
    if (this.saved || this.store.loadState() !== 'ready') {
      return false;
    }
    return this.store.dirty() || !!this.details()?.hasChanges() || !!this.place()?.hasChanges();
  }

  private listCommands(): string[] {
    return ['/', this.locale.lang(), 'account', 'listings'];
  }

  private async loadCategories(): Promise<void> {
    try {
      const families = await this.referentials.categories();
      const lang = this.locale.lang();
      this.categoryLabels.set(
        Object.fromEntries(
          families.flatMap((family) =>
            family.subcategories.map((subcategory) => [
              subcategory.code,
              `${labelOf(family.label, lang)} · ${labelOf(subcategory.label, lang)}`,
            ]),
          ),
        ),
      );
    } catch {
      // The category stays hidden: it cannot be changed anyway.
    }
  }

  private toast(key: string, type: 'success' | 'error'): void {
    combineLatest([
      this.transloco.selectTranslate<string>(key, {}, 'publish'),
      this.transloco.selectTranslate<string>('edit.close', {}, 'publish'),
    ])
      .pipe(take(1))
      .subscribe(([message, closeLabel]) => this.toasts.show({ type, message, closeLabel }));
  }
}
