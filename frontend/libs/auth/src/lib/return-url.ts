/** Longest return URL kept; a longer one is ignored. */
const MAX_RETURN_URL_LENGTH = 2000;

/**
 * The `returnUrl` query parameter when it is an internal path, else null: it must start with one
 * "/" and contain no "//", no backslash and no control character, so that it can never send the
 * visitor to another site (open redirect).
 */
export function safeReturnUrl(value: unknown): string | null {
  if (
    typeof value !== 'string' ||
    value.length > MAX_RETURN_URL_LENGTH ||
    !value.startsWith('/') ||
    value.includes('//') ||
    value.includes('\\') ||
    // eslint-disable-next-line no-control-regex
    /[\u0000-\u001f\u007f]/.test(value)
  ) {
    return null;
  }
  return value;
}
