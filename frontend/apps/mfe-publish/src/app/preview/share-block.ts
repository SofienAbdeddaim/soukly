import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnInit,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import { Listing, ShareNetwork } from '@soukly/listing-data';
import { Caption, SocialApi, SocialConnection } from '@soukly/social-data';
import { UiAlert, UiButton, UiSkeleton } from '@soukly/ui';
import { DraftStore } from '../draft/draft-store';

const NETWORK_ORDER: ShareNetwork[] = ['FACEBOOK', 'INSTAGRAM'];

interface ShareOption {
  network: ShareNetwork;
  name: string;
  checked: boolean;
  needsReconnect: boolean;
}

/**
 * "Share the listing" on the preview step (SOC-03): one checkbox per connected network. The first
 * time the preview of a draft is shown, the networks the member ticks by default (`autoShare`)
 * are saved in `shareTo`; afterwards the saved choice is shown. Each change is saved by the
 * autosave of the draft, with the language of the page as the language of the post text. A
 * network to connect again cannot be ticked. "See the post text" asks social-publisher for the
 * text: the front end never builds it. With Instagram alone, the text is the Instagram caption,
 * with the short address of the listing instead of its link (SOC-05).
 */
@Component({
  selector: 'app-share-block',
  imports: [RouterLink, TranslocoPipe, LocalePathPipe, UiAlert, UiButton, UiSkeleton],
  templateUrl: './share-block.html',
  styleUrl: './share-block.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShareBlock implements OnInit {
  private readonly social = inject(SocialApi);
  private readonly store = inject(DraftStore);
  private readonly locale = inject(LocaleService);
  private readonly document = inject(DOCUMENT);

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  private readonly connections = signal<SocialConnection[]>([]);
  protected readonly caption = signal<Caption | null>(null);
  protected readonly captionState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  /** Id of the draft whose default choice was already saved. */
  private defaultsSavedFor: string | null = null;

  protected readonly listing = this.store.listing;

  protected readonly options = computed<ShareOption[]>(() => {
    const chosen = this.listing()?.shareTo ?? [];
    return NETWORK_ORDER.flatMap((network) => {
      const connection = this.connections().find((candidate) => candidate.network === network);
      if (!connection) {
        return [];
      }
      const needsReconnect = connection.status === 'NEEDS_RECONNECT';
      return [
        {
          network,
          name: connection.displayName,
          checked: !needsReconnect && chosen.includes(network),
          needsReconnect,
        },
      ];
    });
  });

  /** "With your 4 photos", in the plural form of the language. */
  protected readonly photosKey = computed(() => {
    const count = this.listing()?.photos.length ?? 0;
    const form = count ? new Intl.PluralRules(this.locale.intlLocale()).select(count) : 'zero';
    return `publish.share.caption.photos.${form}`;
  });

  /**
   * The text shown is the Instagram caption (no link, the short reference instead, SOC-05) when
   * Instagram is the only network ticked, otherwise the Facebook text.
   */
  protected readonly captionNetwork = computed<ShareNetwork>(() => {
    const checked = this.options().filter((option) => option.checked);
    return checked.length === 1 && checked[0].network === 'INSTAGRAM' ? 'INSTAGRAM' : 'FACEBOOK';
  });

  /** Both networks ticked: the Facebook text is shown, with a word on the Instagram one. */
  protected readonly instagramNote = computed(
    () =>
      this.captionNetwork() === 'FACEBOOK' &&
      this.options().some((option) => option.checked && option.network === 'INSTAGRAM'),
  );

  /** The way back to this step after connecting again on "My networks". */
  protected readonly returnTo = computed(() => this.document.location.pathname);

  constructor() {
    // First display of the preview of a draft: its default choice is the member's preference.
    effect(() => {
      const listing = this.listing();
      if (this.state() === 'ready' && listing) {
        untracked(() => this.saveDefaults(listing));
      }
    });
  }

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.state.set('loading');
    try {
      this.connections.set(await this.social.connections());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  protected toggle(network: ShareNetwork, checked: boolean): void {
    const chosen = new Set(
      this.options()
        .filter((option) => option.checked)
        .map((option) => option.network),
    );
    if (checked) {
      chosen.add(network);
    } else {
      chosen.delete(network);
    }
    this.choose(NETWORK_ORDER.filter((candidate) => chosen.has(candidate)));
    // The text shown was for the previous language, maybe: it is read again on the next opening
    this.caption.set(null);
    this.captionState.set('idle');
  }

  /** "See the post text": the text is read when the element opens. */
  protected onCaptionToggle(event: Event): void {
    if ((event.target as HTMLDetailsElement).open && this.captionState() !== 'loading') {
      void this.loadCaption();
    }
  }

  protected async loadCaption(): Promise<void> {
    const listing = this.listing();
    if (!listing) {
      return;
    }
    this.captionState.set('loading');
    try {
      const lang = this.locale.lang();
      this.caption.set(
        await this.social.captionPreview({
          title: listing.title,
          description: listing.description,
          priceMode: listing.priceMode,
          priceTnd: listing.priceMode === 'FIXED' ? listing.price : null,
          negotiable: listing.negotiable,
          categoryCode: listing.categoryCode,
          governorateCode: listing.governorateCode,
          delegationCode: listing.delegationCode,
          publicUrl: `${this.document.location.origin}/${lang}/listings/${listing.id}`,
          language: lang,
          network: this.captionNetwork(),
          shortRef: listing.shortRef ?? null,
        }),
      );
      this.captionState.set('ready');
    } catch {
      this.captionState.set('error');
    }
  }

  private saveDefaults(listing: Listing): void {
    if (listing.shareTo != null || this.defaultsSavedFor === listing.id) {
      return;
    }
    const defaults = this.connections()
      .filter((connection) => connection.status === 'CONNECTED' && connection.autoShare)
      .map((connection) => connection.network);
    if (!this.connections().length) {
      // Nothing to choose yet: the default is saved once a network is connected
      return;
    }
    this.defaultsSavedFor = listing.id;
    this.choose(NETWORK_ORDER.filter((network) => defaults.includes(network)));
  }

  private choose(shareTo: ShareNetwork[]): void {
    void this.store.save({ shareTo, shareLanguage: this.locale.lang() });
  }
}
