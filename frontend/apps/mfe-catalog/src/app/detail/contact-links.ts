import { Lang, localizePath } from '@soukly/i18n';

/** Query parameter that asks the listing page to show the number once signed in (CATA-03). */
export const CONTACT_PARAM = 'contact';

/**
 * A phone number in the national format of Tunisia, readable at a glance: `+21620123456` ->
 * `+216 20 123 456`. Any other number is shown as given. Display it left to right
 * (`<bdi dir="ltr">`), Arabic page included.
 */
export function formatPhone(e164: string): string {
  const match = /^\+216(\d{2})(\d{3})(\d{3})$/.exec(e164);
  return match ? `+216 ${match[1]} ${match[2]} ${match[3]}` : e164;
}

/** `tel:+21620123456`. */
export function callLink(e164: string): string {
  return `tel:${e164}`;
}

/** WhatsApp chat with the seller, the message filled in: the number without "+" (`wa.me`). */
export function whatsappLink(e164: string, message: string): string {
  return `https://wa.me/${e164.replace(/\D/g, '')}?text=${encodeURIComponent(message)}`;
}

/** Path of a listing page, `/fr/listings/{id}`. */
export function listingPath(lang: Lang, id: string): string {
  return localizePath(`/listings/${encodeURIComponent(id)}`, lang);
}

/**
 * Login page for a visitor who asked for the number: back on the listing afterwards, with
 * `contact=1` so that the number shows on its own (`/fr/account/login?returnUrl=…contact%3D1`).
 */
export function contactLoginUrl(lang: Lang, id: string): string {
  const back = `${listingPath(lang, id)}?${CONTACT_PARAM}=1`;
  return `${localizePath('/account/login', lang)}?returnUrl=${encodeURIComponent(back)}`;
}
