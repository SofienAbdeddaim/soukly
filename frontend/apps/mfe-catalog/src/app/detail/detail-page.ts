import { Dialog } from '@angular/cdk/dialog';
import { createGlobalPositionStrategy } from '@angular/cdk/overlay';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DOCUMENT,
  effect,
  inject,
  Injector,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Location, NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LucideArrowLeft, LucideArrowRight, LucideShare2 } from '@lucide/angular';
import { AuthService } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import { ListingPhoto } from '@soukly/listing-data';
import { ToastService, UiButton, UiSkeleton } from '@soukly/ui';
import { combineLatest, map, take } from 'rxjs';
import { PageMeta } from '../shared/page-meta';
import { injectIsDesktop } from '../shared/viewport';
import { CONTACT_PARAM, contactLoginUrl, listingPath } from './contact-links';
import { ContactPanel } from './contact-panel/contact-panel';
import { ContactSheet, ContactSheetData } from './contact-sheet/contact-sheet';
import { Gallery } from './gallery/gallery';
import { ListingDescription } from './listing-description/listing-description';
import { ListingGone } from './listing-gone/listing-gone';
import { ListingPageStore } from './listing-page-store';
import { ListingPageViews } from './listing-page-view';
import { PhotoViewer, PhotoViewerData } from './photo-viewer/photo-viewer';

/** Characters of the description kept in `<meta name="description">`. */
export const META_DESCRIPTION_LENGTH = 155;

/**
 * Page of a listing (`/{lang}/listings/{id}`, CATA-03): photos, price, place, specific
 * attributes, description and seller, with "Show the number" for signed-in members (fixed at the
 * bottom on mobile, in the right column on desktop). A visitor goes through the login and comes
 * back with `contact=1`, which shows the number on its own then leaves the URL. A listing that is
 * gone shows similar ones; an unknown one, a "not found" page.
 */
@Component({
  selector: 'app-detail-page',
  imports: [
    NgTemplateOutlet,
    RouterLink,
    TranslocoPipe,
    LucideArrowLeft,
    LucideArrowRight,
    LucideShare2,
    UiButton,
    UiSkeleton,
    ContactPanel,
    Gallery,
    ListingDescription,
    ListingGone,
  ],
  templateUrl: './detail-page.html',
  styleUrl: './detail-page.css',
  providers: [ListingPageStore, PageMeta],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly dialog = inject(Dialog);
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);
  private readonly transloco = inject(TranslocoService);
  private readonly toasts = inject(ToastService);
  private readonly pageMeta = inject(PageMeta);
  private readonly views = inject(ListingPageViews);
  protected readonly auth = inject(AuthService);
  protected readonly locale = inject(LocaleService);
  protected readonly store = inject(ListingPageStore);
  protected readonly desktop = injectIsDesktop();

  private readonly gallery = viewChild(Gallery);

  /** Whether the page was reached inside the app: then "back" goes back to it (search kept). */
  private readonly cameFromApp = !!this.router.lastSuccessfulNavigation()?.previousNavigation;

  protected readonly id = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('id') ?? '')),
    {
      requireSync: true,
    },
  );

  protected readonly view = computed(() => {
    const listing = this.store.listing();
    return listing ? this.views.view(listing) : null;
  });

  protected readonly photos = computed<readonly ListingPhoto[]>(
    () => this.store.listing()?.photos ?? [],
  );

  protected readonly myListingsCommands = computed(() => [
    '/',
    this.locale.lang(),
    'account',
    'listings',
  ]);
  protected readonly homeCommands = computed(() => ['/', this.locale.lang()]);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.open(id));
    });
    effect(() => {
      const listing = this.store.listing();
      if (listing) {
        untracked(() => this.views.prepare(listing));
      } else if (this.store.status() === 'gone') {
        untracked(() => this.views.prepareBasics());
      }
    });
    effect(() => this.updateMeta());
  }

  protected back(): void {
    if (this.cameFromApp) {
      this.location.back();
    } else {
      void this.router.navigate(this.homeCommands());
    }
  }

  protected login(): void {
    void this.router.navigateByUrl(contactLoginUrl(this.locale.lang(), this.id()));
  }

  protected async reveal(): Promise<void> {
    if (await this.store.reveal()) {
      this.openContactSheet();
    }
  }

  protected openViewer(index: number): void {
    const view = this.view();
    if (!view) {
      return;
    }
    const data: PhotoViewerData = { photos: this.photos(), title: view.title, index };
    const dialogRef = this.dialog.open<number, PhotoViewerData, PhotoViewer>(PhotoViewer, {
      data,
      injector: this.injector,
      ariaLabelledBy: 'photo-viewer-title',
      width: '100vw',
      height: '100dvh',
      maxWidth: '100vw',
      maxHeight: '100dvh',
      panelClass: 'photo-viewer-panel',
    });
    dialogRef.closed.subscribe((shown) => {
      if (shown !== undefined) {
        this.gallery()?.show(shown);
      }
    });
  }

  /** Web Share API when the device has one, else the link is copied with a "Link copied" toast. */
  protected async share(): Promise<void> {
    const view = this.view();
    const url = this.pageUrl();
    const navigator = this.document.defaultView?.navigator;
    if (navigator?.share) {
      try {
        await navigator.share({ title: view?.title, url });
      } catch {
        // Cancelled by the visitor
      }
      return;
    }
    let copied = false;
    try {
      await navigator?.clipboard?.writeText(url);
      copied = !!navigator?.clipboard;
    } catch {
      copied = false;
    }
    this.toast(copied ? 'detail.link-copied' : 'detail.link-copy-failed', copied);
  }

  private async open(id: string): Promise<void> {
    await this.store.load(id);
    const contact = this.route.snapshot.queryParamMap.get(CONTACT_PARAM);
    if (contact === null) {
      return;
    }
    // Back from the login: the number shows on its own, then the parameter leaves the URL
    if (this.store.status() === 'ready' && this.auth.isAuthenticated() && !this.store.owned()) {
      void this.reveal();
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [CONTACT_PARAM]: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private openContactSheet(): void {
    const view = this.view();
    const contact = this.store.contact();
    if (!view?.seller || contact.kind !== 'shown') {
      return;
    }
    const data: ContactSheetData = {
      phone: contact.phone,
      sellerName: view.seller.displayName,
      sellerInitial: view.seller.initial,
      title: view.title,
      url: this.pageUrl(),
    };
    this.dialog.open<void, ContactSheetData, ContactSheet>(ContactSheet, {
      data,
      injector: this.injector,
      ariaLabelledBy: 'contact-sheet-title',
      width: this.desktop() ? '28rem' : '100vw',
      maxWidth: '100vw',
      panelClass: 'contact-sheet-panel',
      // A bottom sheet on mobile, centered on desktop
      positionStrategy: this.desktop()
        ? undefined
        : createGlobalPositionStrategy(this.injector).bottom('0').centerHorizontally(),
    });
  }

  /** Address of the listing in the language of the page, without query parameters. */
  private pageUrl(): string {
    return `${this.document.location.origin}${listingPath(this.locale.lang(), this.id())}`;
  }

  private toast(key: string, success: boolean): void {
    combineLatest([
      this.transloco.selectTranslate<string>(key, {}, 'catalog'),
      this.transloco.selectTranslate<string>('actions.close', {}, 'catalog'),
    ])
      .pipe(take(1))
      .subscribe(([message, closeLabel]) =>
        this.toasts.show({ type: success ? 'success' : 'error', message, closeLabel }),
      );
  }

  /** "{title} · {price} · {governorate} · Soukly" and the beginning of the description. */
  private updateMeta(): void {
    const status = this.store.status();
    const view = this.view();
    if (status === 'ready' && view) {
      this.pageMeta.set(
        view.price ? 'seo.listing-title' : 'seo.listing-title-no-price',
        'seo.listing-description',
        {
          title: view.title,
          price: view.price ?? '',
          place: view.governorate,
          description: shorten(view.description, META_DESCRIPTION_LENGTH),
        },
      );
    } else if (status === 'gone') {
      this.pageMeta.set('seo.gone-title', 'seo.gone-description');
    } else if (status === 'not-found') {
      this.pageMeta.set('seo.not-found-title', 'seo.gone-description');
    }
  }
}

/** The text on one line, cut at a word before `length` characters, with an ellipsis. */
export function shorten(text: string, length: number): string {
  const flat = text.replace(/\s+/g, ' ').trim();
  if (flat.length <= length) {
    return flat;
  }
  const cut = flat.slice(0, length - 1);
  const space = cut.lastIndexOf(' ');
  return `${(space > length / 2 ? cut.slice(0, space) : cut).trimEnd()}…`;
}
