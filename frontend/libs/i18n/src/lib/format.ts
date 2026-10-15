/**
 * Tunisian formats through `Intl` only (no formatting library). Latin digits in every language.
 * The exact output depends on the ICU version of the browser, e.g. 1250 dinars:
 * fr-TN "1 250 DT", ar-TN "1.250 د.ت.", en-TN "TND 1,250".
 */
const formatters = new Map<
  string,
  Intl.NumberFormat | Intl.DateTimeFormat | Intl.RelativeTimeFormat
>();

function cached<T extends Intl.NumberFormat | Intl.DateTimeFormat | Intl.RelativeTimeFormat>(
  key: string,
  create: () => T,
): T {
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = create();
    formatters.set(key, formatter);
  }
  return formatter as T;
}

export type DateInput = Date | string | number;

/** Latin digits (0-9) in every language, Arabic included: Unicode extension "-u-nu-latn". */
function withLatinDigits(locale: string): string {
  return `${locale}-u-nu-latn`;
}

function toDate(value: DateInput): Date {
  return value instanceof Date ? value : new Date(value);
}

/** Price in whole dinars (TND, no millimes), never shown with decimals. */
export function formatPrice(dinars: number, locale: string): string {
  return cached(
    `price|${locale}`,
    () =>
      new Intl.NumberFormat(withLatinDigits(locale), {
        style: 'currency',
        currency: 'TND',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
      }),
  ).format(dinars);
}

/** Date with `dateStyle: 'medium'`: "12 oct. 2026", "12/10/2026", "Oct 12, 2026". */
export function formatLocalDate(value: DateInput, locale: string): string {
  return cached(
    `date|${locale}`,
    () => new Intl.DateTimeFormat(withLatinDigits(locale), { dateStyle: 'medium' }),
  ).format(toDate(value));
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
  ['second', 1],
];

/** Time relative to `now`, in the largest fitting unit: "hier", "il y a 2 heures", "أمس". */
export function formatRelativeTime(
  value: DateInput,
  locale: string,
  now: number = Date.now(),
): string {
  const seconds = (toDate(value).getTime() - now) / 1000;
  const [unit, size] = UNITS.find(([, size]) => Math.abs(seconds) >= size) ?? ['second', 1];
  return cached(
    `relative|${locale}`,
    () => new Intl.RelativeTimeFormat(withLatinDigits(locale), { numeric: 'auto' }),
  ).format(Math.round(seconds / size), unit);
}
