import { Dialog } from '@angular/cdk/dialog';
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LucideArrowLeft, LucidePlus } from '@lucide/angular';
import { LocalePathPipe, LocaleService, localizePath } from '@soukly/i18n';
import { ListingsApi, OwnListingCard } from '@soukly/listing-data';
import { ToastService, UiAlert, UiButton, UiSkeleton, UiToastType } from '@soukly/ui';
import { combineLatest, firstValueFrom, map, take } from 'rxjs';
import { DeleteChoice, DeleteDialog, DeleteDialogData } from './delete-dialog';
import { ListingTabs } from './listing-tabs';
import { MyListingsStore } from './my-listings-store';
import { ListingCommand, OwnListingItem } from './own-listing-item';
import { statusOf, Tab, tabOf } from './own-listing-state';

/** How long the "Undo" of a deactivation stays offered (ANN-05: 5 seconds). */
export const UNDO_DURATION_MS = 5000;

/**
 * "My listings" (`/{lang}/account/listings`, ANN-05): the listings of the member by tab (online,
 * drafts, expired, deactivated), the tab in the URL (`?tab=expired`, "online" by default, as the
 * link of the reminder email opens it).
 *
 * Deactivating needs no confirmation, but its toast offers "Undo" for 5 seconds; deleting asks
 * first in an alert dialog, which offers to deactivate instead when the listing is online.
 */
@Component({
  selector: 'app-my-listings-page',
  imports: [
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideArrowLeft,
    LucidePlus,
    UiAlert,
    UiButton,
    UiSkeleton,
    ListingTabs,
    OwnListingItem,
  ],
  providers: [MyListingsStore],
  templateUrl: './my-listings-page.html',
  styleUrl: './my-listings-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyListingsPage {
  protected readonly store = inject(MyListingsStore);
  private readonly api = inject(ListingsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly dialog = inject(Dialog);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);
  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');

  protected readonly tab = toSignal(
    this.route.queryParamMap.pipe(map((params) => tabOf(params.get('tab')))),
    { initialValue: tabOf(this.route.snapshot.queryParamMap.get('tab')) },
  );
  /** Time the days left are counted from, taken at each read of the tab. */
  protected readonly now = signal(Date.now());
  /** The card whose action is running. */
  protected readonly busyId = signal<string | null>(null);
  protected readonly skeletons = [0, 1, 2];
  protected readonly panelId = 'listings-panel';
  protected readonly tabId = computed(() => `listings-tab-${this.tab()}`);

  constructor() {
    effect(() => {
      const status = statusOf(this.tab());
      untracked(() => {
        this.now.set(Date.now());
        void this.store.open(status);
      });
    });
  }

  protected select(tab: Tab): void {
    if (tab !== this.tab()) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { tab },
        replaceUrl: true,
      });
    }
  }

  protected retry(): void {
    void this.store.open(statusOf(this.tab()));
  }

  protected run(card: OwnListingCard, command: ListingCommand): void {
    switch (command) {
      case 'renew':
        void this.renew(card);
        break;
      case 'share':
        void this.share(card);
        break;
      case 'deactivate':
        void this.deactivate(card);
        break;
      case 'delete':
        void this.confirmDelete(card);
        break;
    }
  }

  private async renew(card: OwnListingCard): Promise<void> {
    if (await this.act(card, () => this.api.renew(card.id))) {
      this.toast('listings.toasts.renewed', 'success');
    }
  }

  private async deactivate(card: OwnListingCard): Promise<void> {
    if (!(await this.act(card, () => this.api.deactivate(card.id)))) {
      return;
    }
    // "Undo" puts the listing back online with renew, the only way back from INACTIVE.
    this.toast('listings.toasts.deactivated', 'success', {
      key: 'listings.toasts.undo',
      run: () => void this.undoDeactivate(card),
      duration: UNDO_DURATION_MS,
    });
  }

  private async undoDeactivate(card: OwnListingCard): Promise<void> {
    if (await this.act(card, () => this.api.renew(card.id), false)) {
      this.toast('listings.toasts.reactivated', 'success');
    }
  }

  private async confirmDelete(card: OwnListingCard): Promise<void> {
    const title =
      card.title ?? (await firstValueFrom(this.translate('listings.untitled')).catch(() => ''));
    const data: DeleteDialogData = { title, canDeactivate: card.status === 'PUBLISHED' };
    const dialogRef = this.dialog.open<DeleteChoice, DeleteDialogData, DeleteDialog>(DeleteDialog, {
      data,
      injector: this.injector,
      role: 'alertdialog',
      ariaLabelledBy: 'delete-dialog-title',
      ariaDescribedBy: 'delete-dialog-text',
      maxWidth: '100vw',
    });
    const choice = await firstValueFrom(dialogRef.closed);
    if (choice === 'deactivate') {
      await this.deactivate(card);
    } else if (choice === 'delete') {
      if (await this.act(card, () => this.api.delete(card.id))) {
        this.toast('listings.toasts.deleted', 'success');
      }
    }
  }

  /**
   * Runs an action on a card, then reads the tab again for the new counts. When `leaves` (the
   * card no longer belongs to the tab) it disappears at once and the focus goes to the panel.
   */
  private async act(
    card: OwnListingCard,
    action: () => Promise<unknown>,
    leaves = true,
  ): Promise<boolean> {
    this.busyId.set(card.id);
    try {
      await action();
    } catch {
      this.busyId.set(null);
      this.toast('listings.toasts.failed', 'error');
      // The listing may have changed elsewhere (deleted, renewed): show it as it is now.
      void this.store.refresh();
      return false;
    }
    this.busyId.set(null);
    const onTab = this.store.items().some((item) => item.id === card.id);
    if (leaves && onTab && card.status === this.store.status()) {
      this.store.remove(card.id);
      this.panel()?.nativeElement.focus();
    }
    this.now.set(Date.now());
    void this.store.refresh();
    return true;
  }

  /** Web Share API when the device has one, else the link is copied with a toast. */
  private async share(card: OwnListingCard): Promise<void> {
    const url = `${this.document.location.origin}${localizePath(
      `/listings/${encodeURIComponent(card.id)}`,
      this.locale.lang(),
    )}`;
    const navigator = this.document.defaultView?.navigator;
    if (navigator?.share) {
      try {
        await navigator.share({ title: card.title ?? undefined, url });
      } catch {
        // Cancelled by the member
      }
      return;
    }
    try {
      await navigator?.clipboard?.writeText(url);
      this.toast(
        navigator?.clipboard ? 'listings.toasts.link-copied' : 'listings.toasts.link-failed',
        navigator?.clipboard ? 'success' : 'error',
      );
    } catch {
      this.toast('listings.toasts.link-failed', 'error');
    }
  }

  private translate(key: string) {
    return this.transloco.selectTranslate<string>(key, {}, 'account').pipe(take(1));
  }

  private toast(
    key: string,
    type: UiToastType,
    action?: { key: string; run: () => void; duration: number },
  ): void {
    combineLatest([
      this.translate(key),
      this.translate('listings.toasts.close'),
      this.translate(action?.key ?? 'listings.toasts.close'),
    ]).subscribe(([message, closeLabel, actionLabel]) =>
      this.toasts.show({
        type,
        message,
        closeLabel,
        duration: action?.duration,
        action: action ? { label: actionLabel, run: action.run } : undefined,
      }),
    );
  }
}
