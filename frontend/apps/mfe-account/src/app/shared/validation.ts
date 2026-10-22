import { ReadonlyFieldTree, ValidationError } from '@angular/forms/signals';

/** Tunisian numbers have 8 digits after +216. */
const TUNISIAN_DIGITS = /^\d{8}$/;
/** Simple format check; user-service has the last word. */
const EMAIL_FORMAT = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EMAIL_MAX = 254;

/**
 * The 8 digits of a Tunisian number typed after the fixed +216 prefix, spaces, dots and dashes
 * allowed ("20 123 456"); a pasted +216 or 00216 is accepted too. Null when it is not 8 digits.
 */
export function tunisianDigits(value: string): string | null {
  const compact = value.replace(/[\s.()-]/g, '').replace(/^(\+216|00216)/, '');
  return TUNISIAN_DIGITS.test(compact) ? compact : null;
}

export function isEmailFormat(value: string): boolean {
  const email = value.trim();
  return email.length <= EMAIL_MAX && EMAIL_FORMAT.test(email);
}

/** Length in characters as the API counts them (code points, after trimming for names). */
export function codePointLength(value: string): number {
  return [...value].length;
}

/** An error whose `kind` is the stable code of the API (`phone.invalid`), translated by the page. */
export function codeError(code: string): ValidationError {
  return { kind: code };
}

/**
 * Translation key of the first error of a field, once the visitor has left it (or tried to submit):
 * `account.errors.phone.invalid`. Null otherwise.
 */
export function errorKey(field: ReadonlyFieldTree<unknown>): string | null {
  const state = field();
  const first = state.touched() ? state.errors()[0] : undefined;
  return first ? `account.errors.${first.kind}` : null;
}

/** Puts the focus on the first field of the list that has an error. */
export function focusFirstInvalid(fields: ReadonlyFieldTree<unknown>[]): void {
  fields
    .find((field) => field().invalid())?.()
    .focusBoundControl();
}

/** The 8 digits of an E.164 Tunisian number as typed after +216: "+21620123456" -> "20 123 456". */
export function nationalPhone(e164: string): string {
  const digits = e164.replace(/^\+216/, '');
  return TUNISIAN_DIGITS.test(digits)
    ? `${digits.slice(0, 2)} ${digits.slice(2, 5)} ${digits.slice(5)}`
    : digits;
}
