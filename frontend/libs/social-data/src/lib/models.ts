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

/** Status of a post of a listing on one network (SOC-04, SOC-05). */
export type PostStatus = 'PENDING' | 'PUBLISHED' | 'FAILED' | 'SKIPPED' | 'CANCELLED' | 'DELETED';

/** The pill shown on "My listings" (SOC-06), computed by social-publisher. */
export type PostBadge = 'PUBLISHED' | 'PENDING' | 'TO_RECONNECT' | 'FAILED';

/**
 * Why a post failed, never the raw message of Meta: the retries gave up, a photo was refused, the
 * network was not connected (then connected again), or the network refused the post.
 */
export type PostFailureReason =
  'RETRIES_EXHAUSTED' | 'MEDIA_REJECTED' | 'NOT_CONNECTED' | 'REJECTED';

/** A post of a listing, as `GET /social/posts` returns it (SOC-06). */
export interface PostStatusView {
  network: SocialNetwork;
  status: PostStatus;
  badge: PostBadge;
  /** Set on a `FAILED` badge only. */
  reason?: PostFailureReason;
  /** Link of the post, on a `PUBLISHED` badge only. */
  permalink?: string;
  errorCode?: string;
  retryable: boolean;
  manualRetriesLeft: number;
  /** ISO instant. */
  updatedAt: string;
}

/** The posts of one listing, Facebook first; a listing without post is not in the answer. */
export interface ListingPosts {
  listingId: string;
  posts: PostStatusView[];
}

/** A listing still online whose post was blocked while the network was not connected. */
export interface BlockedListing {
  listingId: string;
  title: string | null;
}

/** The listings that can be posted again after a reconnection (SOC-06). */
export interface BlockedListings {
  network: SocialNetwork;
  total: number;
  listings: BlockedListing[];
}

/** Stable reasons of a refused retry, the problem types of social-publisher, plus `unknown`. */
export type RetryFailure =
  'not-retryable' | 'retry-limit-reached' | 'listing-not-online' | 'post-not-found' | 'unknown';
