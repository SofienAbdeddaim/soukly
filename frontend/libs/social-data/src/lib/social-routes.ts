import { FacebookFailure, InstagramFailure } from './models';

/** Route of the shell that Facebook redirects to, without a language prefix (SOC-01). */
export const FACEBOOK_CALLBACK_PATH = 'oauth/facebook/callback';

/** Route of the shell that Instagram redirects to, without a language prefix (SOC-02). */
export const INSTAGRAM_CALLBACK_PATH = 'oauth/instagram/callback';

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

/**
 * Query parameter of the social page that tells how the return from Instagram ended: `connected`
 * or an {@link InstagramFailure}.
 */
export const INSTAGRAM_RESULT_PARAM = 'instagram';

export const INSTAGRAM_FAILURES: readonly InstagramFailure[] = [
  'oauth-state-invalid',
  'oauth-cancelled',
  'oauth-permission-missing',
  'instagram-not-professional',
  'instagram-unavailable',
  'instagram-not-configured',
  'unknown',
];

/** A known failure, `unknown` for any other problem type or error. */
export function toInstagramFailure(code: string | null | undefined): InstagramFailure {
  return INSTAGRAM_FAILURES.find((failure) => failure === code) ?? 'unknown';
}
