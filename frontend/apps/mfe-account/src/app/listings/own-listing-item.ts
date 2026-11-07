import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  LucideEllipsis,
  LucideEye,
  LucideImage,
  LucidePause,
  LucidePencil,
  LucideShare2,
  LucideTrash,
} from '@lucide/angular';
import { LocalDatePipe, LocaleService, PricePipe } from '@soukly/i18n';
import { OwnListingCard } from '@soukly/listing-data';
import { PostStatusView, SocialNetwork } from '@soukly/social-data';
import { UiBadge, UiButton, UiSkeleton } from '@soukly/ui';
import { pluralKey } from '../shared/plural';
import { badgeOf, primaryActionOf } from './own-listing-state';
import { SocialPostBadges } from './social-post-badges';

/** Actions of a card handled by the page; the links (edit, continue, view) navigate on their own. */
export type ListingCommand = 'renew' | 'share' | 'deactivate' | 'delete';

/**
 * One listing on "My listings" (ANN-05): cover, title, price, state pill, numbers shown, the main
 * action of its state and the "⋯" menu (CDK menu: arrow keys, Escape, focus back on the button).
 * Under them, where the listing was posted on the networks (SOC-06).
 */
@Component({
  selector: 'app-own-listing-item',
  imports: [
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    RouterLink,
    TranslocoPipe,
    LocalDatePipe,
    PricePipe,
    LucideEllipsis,
    LucideEye,
    LucideImage,
    LucidePause,
    LucidePencil,
    LucideShare2,
    LucideTrash,
    UiBadge,
    UiButton,
    UiSkeleton,
    SocialPostBadges,
  ],
  templateUrl: './own-listing-item.html',
  styleUrl: './own-listing-item.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-testid': 'own-listing', '[attr.data-status]': 'card().status' },
})
export class OwnListingItem {
  readonly card = input.required<OwnListingCard>();
  /** Current time in milliseconds, for the days left. */
  readonly now = input.required<number>();
  /** An action of this card is running: its buttons wait. */
  readonly busy = input(false);
  readonly requested = output<ListingCommand>();
  /** Posts of the listing on the networks (SOC-06); empty when none, or when they could not be read. */
  readonly posts = input<PostStatusView[]>([]);
  /** The posts are being read: a discreet skeleton. */
  readonly postsLoading = input(false);
  /** The network of this listing being queued again. */
  readonly retryingNetwork = input<SocialNetwork | null>(null);
  readonly retryPost = output<SocialNetwork>();

  private readonly locale = inject(LocaleService);

  protected readonly badge = computed(() => badgeOf(this.card(), this.now()));
  protected readonly primary = computed(() => primaryActionOf(this.card(), this.now()));
  protected readonly badgeKey = computed(() => {
    const badge = this.badge();
    const base = `account.listings.badge.${badge.key}`;
    return badge.count === undefined ? base : pluralKey(base, badge.count, this.locale.lang());
  });
  protected readonly revealsKey = computed(() =>
    // "No number shown yet" rather than "0 numéro affiché" (French puts 0 in the "one" form).
    this.card().contactRevealsCount === 0
      ? 'account.listings.reveals-none'
      : pluralKey('account.listings.reveals', this.card().contactRevealsCount, this.locale.lang()),
  );
  /** Where the title leads: the public page when online, else the form. */
  protected readonly titleCommands = computed(() => {
    const { id, status } = this.card();
    const lang = this.locale.lang();
    switch (status) {
      case 'PUBLISHED':
        return ['/', lang, 'listings', id];
      case 'DRAFT':
        return this.continueCommands();
      default:
        return this.editCommands();
    }
  });
  protected readonly editCommands = computed(() => [
    '/',
    this.locale.lang(),
    'publish',
    this.card().id,
    'edit',
  ]);
  /** A draft opens on its first incomplete step (ANN-04). */
  protected readonly continueCommands = computed(() => [
    '/',
    this.locale.lang(),
    'publish',
    this.card().id,
  ]);
  protected readonly viewCommands = computed(() => [
    '/',
    this.locale.lang(),
    'listings',
    this.card().id,
  ]);
  protected readonly titleId = computed(() => `own-listing-${this.card().id}`);
}
