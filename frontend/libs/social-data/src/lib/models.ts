/** Social networks a member can connect: a Facebook Page (SOC-01), an Instagram account (SOC-02). */
export type SocialNetwork = 'FACEBOOK' | 'INSTAGRAM';

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
  /** Ticked by default on the preview of a new listing (SOC-03). */
  autoShare: boolean;
}

/**
 * What the post text of a listing is made of (SOC-03): the fields of the draft, the link of its
 * page and the language of the text. Never the name or the phone number of the seller.
 */
export interface CaptionRequest {
  title: string | null;
  description: string | null;
  priceMode: 'FIXED' | 'ON_QUOTE' | null;
  priceTnd: number | null;
  negotiable: boolean;
  categoryCode: string | null;
  governorateCode: string | null;
  delegationCode: string | null;
  publicUrl: string;
  language: string;
  /** `FACEBOOK` by default; `INSTAGRAM` gives the caption without link (SOC-05). */
  network?: 'FACEBOOK' | 'INSTAGRAM';
  /** Short reference of the draft, written in the Instagram caption. */
  shortRef?: string | null;
}

/** The text posted on the networks, built by social-publisher (the front end never builds it). */
export interface Caption {
  text: string;
  hashtags: string[];
}

/** A Page offered when the member manages several. */
export interface PageCandidate {
  pageId: string;
  name: string;
  category: string | null;
  pictureUrl: string | null;
}

/** What the network sent back to its return route: `code` and `state`, or `error`. */
export interface OAuthRedirect {
  code: string | null;
  state: string | null;
  error: string | null;
}

/** What Facebook sent back to `/oauth/facebook/callback`. */
export type FacebookRedirect = OAuthRedirect;

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

/**
 * Stable reasons of a failed Instagram connection (SOC-02), the problem types of
 * social-publisher, plus `unknown` (network error, unexpected answer).
 */
export type InstagramFailure =
  | 'oauth-state-invalid'
  | 'oauth-cancelled'
  | 'oauth-permission-missing'
  | 'instagram-not-professional'
  | 'instagram-unavailable'
  | 'instagram-not-configured'
  | 'unknown';

/**
 * End of the return from Instagram. `lang` is the language of the page that started the
 * connection, null when the state was not valid.
 */
export type InstagramCallbackOutcome =
  | { kind: 'connected'; lang: string; connection: SocialConnection }
  | { kind: 'failed'; lang: string | null; failure: InstagramFailure };
