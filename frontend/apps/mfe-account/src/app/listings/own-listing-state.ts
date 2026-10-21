import { OWN_LISTING_STATUSES, OwnListingCard, OwnListingStatus } from '@soukly/listing-data';
import { UiBadgeTone } from '@soukly/ui';

/** Tabs of "My listings", as they appear in the URL (`?tab=expired`), in the order of the tabs. */
export const TABS = ['online', 'drafts', 'expired', 'inactive'] as const;
export type Tab = (typeof TABS)[number];

const STATUS_OF: Record<Tab, OwnListingStatus> = {
  online: 'PUBLISHED',
  drafts: 'DRAFT',
  expired: 'EXPIRED',
  inactive: 'INACTIVE',
};

/** The tab of a `?tab=` value; "online" when absent or unknown (link of the reminder email). */
export function tabOf(value: string | null | undefined): Tab {
  return TABS.find((tab) => tab === value) ?? 'online';
}

export function statusOf(tab: Tab): OwnListingStatus {
  return STATUS_OF[tab];
}

export function tabOfStatus(status: OwnListingStatus): Tab {
  return TABS[OWN_LISTING_STATUSES.indexOf(status)];
}

/** How long before the end a published listing may be extended (ANN-03 reminder, 3 days). */
export const RENEW_WINDOW_MS = 3 * 24 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

/** State pill of a card: translation key under `account.listings.badge`, its count and tone. */
export interface BadgeState {
  key: 'online' | 'expires' | 'expired' | 'inactive' | 'draft' | 'draft-ready';
  tone: UiBadgeTone;
  /** Days left (`online`, `expires`) or missing fields (`draft`). */
  count?: number;
  /** End of an expired listing (ISO 8601). */
  date?: string;
}

/** What the main button of a card does. */
export type PrimaryAction = 'edit' | 'extend' | 'renew' | 'continue';

/** True when a published listing expires within the renew window. */
export function expiresSoon(card: OwnListingCard, now: number): boolean {
  return (
    card.status === 'PUBLISHED' &&
    !!card.expiresAt &&
    Date.parse(card.expiresAt) - now <= RENEW_WINDOW_MS
  );
}

/** Whole days left before the end, at least 1 while the listing is still online. */
export function daysLeft(expiresAt: string, now: number): number {
  return Math.max(1, Math.ceil((Date.parse(expiresAt) - now) / DAY_MS));
}

export function badgeOf(card: OwnListingCard, now: number): BadgeState {
  switch (card.status) {
    case 'PUBLISHED': {
      const days = card.expiresAt ? daysLeft(card.expiresAt, now) : 0;
      return expiresSoon(card, now)
        ? { key: 'expires', tone: 'warning', count: days }
        : { key: 'online', tone: 'success', count: days };
    }
    case 'EXPIRED':
      return { key: 'expired', tone: 'danger', date: card.expiresAt ?? card.updatedAt };
    case 'INACTIVE':
      return { key: 'inactive', tone: 'neutral' };
    case 'DRAFT': {
      const missing = card.missingFieldsCount ?? card.missingFields?.length ?? 0;
      return missing
        ? { key: 'draft', tone: 'neutral', count: missing }
        : { key: 'draft-ready', tone: 'neutral' };
    }
  }
}

export function primaryActionOf(card: OwnListingCard, now: number): PrimaryAction {
  switch (card.status) {
    case 'PUBLISHED':
      return expiresSoon(card, now) ? 'extend' : 'edit';
    case 'EXPIRED':
    case 'INACTIVE':
      return 'renew';
    case 'DRAFT':
      return 'continue';
  }
}
