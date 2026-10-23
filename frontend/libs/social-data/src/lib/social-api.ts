import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_PREFIX, problemCode } from '@soukly/auth';
import { firstValueFrom } from 'rxjs';
import {
  BlockedListings,
  Caption,
  CaptionRequest,
  FacebookCallbackOutcome,
  FacebookRedirect,
  InstagramCallbackOutcome,
  ListingPosts,
  OAuthRedirect,
  PageCandidate,
  PostStatusView,
  RetryFailure,
  SocialConnection,
  SocialNetwork,
} from './models';
import { toFacebookFailure, toInstagramFailure } from './social-routes';

interface CallbackBody {
  lang: string;
  connection?: SocialConnection;
  pendingSelection?: PageCandidate[];
}

/**
 * The social networks of the member, in social-publisher (SOC-01 to SOC-03, SOC-06). Every request goes through
 * `API_PREFIX`, so `authInterceptor` adds the access token. Tokens never reach the browser: the
 * OAuth `code` is only forwarded to the API.
 */
@Injectable({ providedIn: 'root' })
export class SocialApi {
  private readonly http = inject(HttpClient);

  connections(): Promise<SocialConnection[]> {
    return firstValueFrom(this.http.get<SocialConnection[]>(`${API_PREFIX}/social/connections`));
  }

  /** Changes "tick by default for every new listing" of a connected network (SOC-03). */
  updateAutoShare(network: SocialNetwork, autoShare: boolean): Promise<SocialConnection> {
    return firstValueFrom(
      this.http.patch<SocialConnection>(
        `${API_PREFIX}/social/connections/${network.toLowerCase()}`,
        { autoShare },
      ),
    );
  }

  /** The text that will be posted for a draft (SOC-03). */
  captionPreview(request: CaptionRequest): Promise<Caption> {
    return firstValueFrom(this.http.post<Caption>(`${API_PREFIX}/social/caption-preview`, request));
  }

  /** Starts a connection and returns the URL of the Facebook dialog to open. */
  async connectFacebook(lang: string): Promise<string> {
    const body = await firstValueFrom(
      this.http.post<{ authorizeUrl: string }>(`${API_PREFIX}/social/facebook/connect`, { lang }),
    );
    return body.authorizeUrl;
  }

  /** Ends the connection with what Facebook sent back; never rejects. */
  async facebookCallback(redirect: FacebookRedirect): Promise<FacebookCallbackOutcome> {
    try {
      const body = await firstValueFrom(
        this.http.post<CallbackBody>(`${API_PREFIX}/social/facebook/callback`, redirect),
      );
      if (body.connection) {
        return { kind: 'connected', lang: body.lang, connection: body.connection };
      }
      return { kind: 'choose', lang: body.lang, pages: body.pendingSelection ?? [] };
    } catch (error) {
      return {
        kind: 'failed',
        lang: problemLang(error),
        failure: toFacebookFailure(problemCode(error)),
      };
    }
  }

  /** The Pages of the choice in progress, null when there is none or it expired. */
  async pageSelection(): Promise<PageCandidate[] | null> {
    try {
      const body = await firstValueFrom(
        this.http.get<{ pendingSelection: PageCandidate[] }>(
          `${API_PREFIX}/social/facebook/page-selection`,
        ),
      );
      return body.pendingSelection;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /** Connects the Page chosen among several; rejects with `page-selection-not-found` when expired. */
  chooseFacebookPage(pageId: string): Promise<SocialConnection> {
    return firstValueFrom(
      this.http.post<SocialConnection>(`${API_PREFIX}/social/facebook/page`, { pageId }),
    );
  }

  disconnectFacebook(): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${API_PREFIX}/social/facebook`));
  }

  /** Starts an Instagram connection and returns the URL of the Instagram dialog to open. */
  async connectInstagram(lang: string): Promise<string> {
    const body = await firstValueFrom(
      this.http.post<{ authorizeUrl: string }>(`${API_PREFIX}/social/instagram/connect`, { lang }),
    );
    return body.authorizeUrl;
  }

  /** Ends the Instagram connection with what Instagram sent back; never rejects. */
  async instagramCallback(redirect: OAuthRedirect): Promise<InstagramCallbackOutcome> {
    try {
      const body = await firstValueFrom(
        this.http.post<{ lang: string; connection: SocialConnection }>(
          `${API_PREFIX}/social/instagram/callback`,
          redirect,
        ),
      );
      return { kind: 'connected', lang: body.lang, connection: body.connection };
    } catch (error) {
      return {
        kind: 'failed',
        lang: problemLang(error),
        failure: toInstagramFailure(problemCode(error)),
      };
    }
  }

  disconnectInstagram(): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${API_PREFIX}/social/instagram`));
  }

  /**
   * The posts of these listings (SOC-06, 50 at most): the listings of other members and those
   * without post are not in the answer.
   */
  async posts(listingIds: readonly string[]): Promise<ListingPosts[]> {
    if (listingIds.length === 0) {
      return [];
    }
    const body = await firstValueFrom(
      this.http.get<{ listings: ListingPosts[] }>(`${API_PREFIX}/social/posts`, {
        params: { listingIds: listingIds.join(',') },
      }),
    );
    return body.listings;
  }

  /** Queues a failed post again; rejects with a {@link RetryError}. */
  async retryPost(listingId: string, network: SocialNetwork): Promise<PostStatusView> {
    try {
      return await firstValueFrom(
        this.http.post<PostStatusView>(
          `${API_PREFIX}/social/posts/${encodeURIComponent(listingId)}/${network}/retry`,
          null,
        ),
      );
    } catch (error) {
      throw new RetryError(toRetryFailure(problemCode(error)));
    }
  }

  /** The listings still online whose post was blocked while the network was not connected. */
  blockedPosts(network: SocialNetwork): Promise<BlockedListings> {
    return firstValueFrom(
      this.http.get<BlockedListings>(`${API_PREFIX}/social/posts/blocked`, {
        params: { network },
      }),
    );
  }

  /** Queues the blocked posts of a network again; returns how many. */
  async relaunchPosts(network: SocialNetwork): Promise<number> {
    const body = await firstValueFrom(
      this.http.post<{ relaunched: number }>(`${API_PREFIX}/social/posts/relaunch`, { network }),
    );
    return body.relaunched;
  }
}

/** A retry refused by social-publisher, with its stable reason. */
export class RetryError extends Error {
  constructor(readonly failure: RetryFailure) {
    super(`Retry refused: ${failure}`);
    this.name = 'RetryError';
  }
}

const RETRY_FAILURES: readonly RetryFailure[] = [
  'not-retryable',
  'retry-limit-reached',
  'listing-not-online',
  'post-not-found',
];

function toRetryFailure(code: string | null): RetryFailure {
  return RETRY_FAILURES.find((failure) => failure === code) ?? 'unknown';
}

function problemLang(error: unknown): string | null {
  if (
    !(error instanceof HttpErrorResponse) ||
    error.error === null ||
    typeof error.error !== 'object'
  ) {
    return null;
  }
  const lang = (error.error as Record<string, unknown>)['lang'];
  return typeof lang === 'string' ? lang : null;
}
