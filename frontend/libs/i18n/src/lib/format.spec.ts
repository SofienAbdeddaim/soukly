import {
  currencySymbol,
  formatLocalDate,
  formatNumber,
  formatPrice,
  formatRelativeTime,
} from './format';

/** Intl puts direction marks (U+200E, U+200F) and non-breaking spaces in its output. */
function plain(text: string): string {
  return text.replace(/[\u200e\u200f]/g, '').replace(/[\u00a0\u202f]/g, ' ');
}

describe('formats', () => {
  describe('price, in whole dinars', () => {
    it('is localized in the three languages, without decimals', () => {
      expect(plain(formatPrice(1250, 'fr-TN'))).toBe('1 250 DT');
      expect(plain(formatPrice(1250, 'ar-TN'))).toBe('1.250 د.ت.');
      expect(plain(formatPrice(1250, 'en-TN'))).toBe('TND 1,250');
    });

    it('never shows millimes', () => {
      expect(plain(formatPrice(180, 'fr-TN'))).toBe('180 DT');
      expect(plain(formatPrice(0, 'en-TN'))).toBe('TND 0');
    });
  });

  it('gives the symbol of the dinar for a price field', () => {
    expect(currencySymbol('fr-TN')).toBe('DT');
    expect(currencySymbol('ar-TN')).toBe('د.ت.');
    expect(currencySymbol('en-TN')).toBe('TND');
  });

  it('groups the thousands of a number in the language', () => {
    expect(plain(formatNumber(32000, 'fr-TN'))).toBe('32 000');
    expect(plain(formatNumber(32000, 'ar-TN'))).toBe('32.000');
    expect(plain(formatNumber(32000, 'en-TN'))).toBe('32,000');
  });

  it('writes digits in Latin script in Arabic too', () => {
    expect(formatPrice(1250, 'ar-TN')).toMatch(/1.250/);
    expect(formatLocalDate('2026-10-12T10:00:00Z', 'ar-TN')).not.toMatch(/[٠-٩]/);
    expect(formatRelativeTime(Date.now() - 3 * 3600_000, 'ar-TN')).toMatch(/3/);
  });

  it('formats a date with dateStyle medium', () => {
    const date = new Date(2026, 9, 12, 10, 0);
    expect(plain(formatLocalDate(date, 'fr-TN'))).toBe('12 oct. 2026');
    expect(plain(formatLocalDate(date, 'ar-TN'))).toBe('12/10/2026');
    expect(plain(formatLocalDate(date, 'en-TN'))).toBe('Oct 12, 2026');
  });

  describe('relative time', () => {
    const now = new Date(2026, 9, 15, 12, 0).getTime();
    const hoursAgo = (hours: number) => now - hours * 3600_000;

    it('says "yesterday" in the three languages', () => {
      expect(formatRelativeTime(hoursAgo(24), 'fr-TN', now)).toBe('hier');
      expect(formatRelativeTime(hoursAgo(24), 'ar-TN', now)).toBe('أمس');
      expect(formatRelativeTime(hoursAgo(24), 'en-TN', now)).toBe('yesterday');
    });

    it('uses the largest fitting unit', () => {
      expect(plain(formatRelativeTime(hoursAgo(2), 'fr-TN', now))).toBe('il y a 2 heures');
      expect(plain(formatRelativeTime(now - 30_000, 'en-TN', now))).toBe('30 seconds ago');
      expect(plain(formatRelativeTime(hoursAgo(72), 'en-TN', now))).toBe('3 days ago');
      expect(plain(formatRelativeTime(hoursAgo(24 * 14), 'en-TN', now))).toBe('2 weeks ago');
      expect(plain(formatRelativeTime(now, 'en-TN', now))).toBe('now');
    });
  });
});
