import { ownListingCard } from '@soukly/listing-data';
import { badgeOf, primaryActionOf, statusOf, tabOf, tabOfStatus } from './own-listing-state';

const NOW = Date.parse('2026-10-21T10:00:00Z');
const DAY = 24 * 60 * 60 * 1000;
const inDays = (days: number) => new Date(NOW + days * DAY).toISOString();

describe('own listing state', () => {
  it('reads the tab of the URL, "online" by default', () => {
    expect(tabOf('expired')).toBe('expired');
    expect(tabOf(null)).toBe('online');
    expect(tabOf('unknown')).toBe('online');
    expect(statusOf('drafts')).toBe('DRAFT');
    expect(tabOfStatus('INACTIVE')).toBe('inactive');
  });

  it('shows the days left of a listing online, and edits it', () => {
    const card = ownListingCard({ expiresAt: inDays(58) });

    expect(badgeOf(card, NOW)).toEqual({ key: 'online', tone: 'success', count: 58 });
    expect(primaryActionOf(card, NOW)).toBe('edit');
  });

  it('offers to extend a listing that expires within 3 days', () => {
    const card = ownListingCard({ expiresAt: inDays(1.5) });

    expect(badgeOf(card, NOW)).toEqual({ key: 'expires', tone: 'warning', count: 2 });
    expect(primaryActionOf(card, NOW)).toBe('extend');
  });

  it('puts an expired or deactivated listing back online', () => {
    const expired = ownListingCard({ status: 'EXPIRED', expiresAt: inDays(-11) });
    const inactive = ownListingCard({ status: 'INACTIVE' });

    expect(badgeOf(expired, NOW)).toEqual({ key: 'expired', tone: 'danger', date: inDays(-11) });
    expect(badgeOf(inactive, NOW)).toEqual({ key: 'inactive', tone: 'neutral' });
    expect(primaryActionOf(expired, NOW)).toBe('renew');
    expect(primaryActionOf(inactive, NOW)).toBe('renew');
  });

  it('counts the missing fields of a draft, which is continued', () => {
    const draft = ownListingCard({ status: 'DRAFT', expiresAt: null, missingFieldsCount: 3 });
    const ready = ownListingCard({ status: 'DRAFT', expiresAt: null, missingFieldsCount: 0 });

    expect(badgeOf(draft, NOW)).toEqual({ key: 'draft', tone: 'neutral', count: 3 });
    expect(badgeOf(ready, NOW)).toEqual({ key: 'draft-ready', tone: 'neutral' });
    expect(primaryActionOf(draft, NOW)).toBe('continue');
  });
});
