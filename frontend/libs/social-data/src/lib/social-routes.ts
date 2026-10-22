import { FacebookFailure } from './models';

/** Route of the shell that Facebook redirects to, without a language prefix (SOC-01). */
export const FACEBOOK_CALLBACK_PATH = 'oauth/facebook/callback';

/** Page of the social networks, after `/{lang}`. */
export const SOCIAL_PAGE_PATH = 'account/social';

/** Choice among several Pages, after `/{lang}`. */
export const FACEBOOK_PAGE_CHOICE_PATH = 'account/social/facebook/page';

/**
 * Query parameter of the social page that tells how the return from Facebook ended: `connected`
 * or a {@link FacebookFailure}.
 */
export const FACEBOOK_RESULT_PARAM = 'facebook';

export const FACEBOOK_FAILURES: readonly FacebookFailure[] = [
  'oauth-state-invalid',
  'oauth-cancelled',
  'oauth-permission-missing',
  'facebook-no-page',
  'facebook-unavailable',
  'facebook-not-configured',
  'page-selection-not-found',
  'unknown',
];

/** A known failure, `unknown` for any other problem type or error. */
export function toFacebookFailure(code: string | null | undefined): FacebookFailure {
  return FACEBOOK_FAILURES.find((failure) => failure === code) ?? 'unknown';
}
