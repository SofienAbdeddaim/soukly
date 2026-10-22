/** Social networks a member can connect (SOC-01; Instagram with SOC-02). */
export type SocialNetwork = 'FACEBOOK';

/** `NEEDS_RECONNECT`: Meta refused the token, the member connects again (set by SOC-04). */
export type ConnectionStatus = 'CONNECTED' | 'NEEDS_RECONNECT';

/** A connected account as `GET /social/connections` returns it: never a token. */
export interface SocialConnection {
  network: SocialNetwork;
  status: ConnectionStatus;
  displayName: string;
  pictureUrl: string | null;
  /** ISO instant. */
  connectedAt: string;
}

/** A Page offered when the member manages several. */
export interface PageCandidate {
  pageId: string;
  name: string;
  category: string | null;
  pictureUrl: string | null;
}

/** What Facebook sent back to `/oauth/facebook/callback`. */
export interface FacebookRedirect {
  code: string | null;
  state: string | null;
  error: string | null;
}

/**
 * Stable reasons of a failed connection, the problem types of social-publisher, plus `unknown`
 * (network error, unexpected answer).
 */
export type FacebookFailure =
  | 'oauth-state-invalid'
  | 'oauth-cancelled'
  | 'oauth-permission-missing'
  | 'facebook-no-page'
  | 'facebook-unavailable'
  | 'facebook-not-configured'
  | 'page-selection-not-found'
  | 'unknown';

/**
 * End of the return from Facebook. `lang` is the language of the page that started the
 * connection, null when the state was not valid.
 */
export type FacebookCallbackOutcome =
  | { kind: 'connected'; lang: string; connection: SocialConnection }
  | { kind: 'choose'; lang: string; pages: PageCandidate[] }
  | { kind: 'failed'; lang: string | null; failure: FacebookFailure };
