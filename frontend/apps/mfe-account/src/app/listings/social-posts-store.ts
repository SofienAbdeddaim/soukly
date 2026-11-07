import { DOCUMENT } from '@angular/common';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import {
  PostStatusView,
  RetryError,
  RetryFailure,
  SocialApi,
  SocialNetwork,
} from '@soukly/social-data';

/** A post "in progress" is read again every 15 seconds (SOC-06)... */
export const POSTS_POLL_INTERVAL_MS = 15_000;
/** ...for 2 minutes at most, counted from the first read that found one, or from a retry. */
export const POSTS_POLL_WINDOW_MS = 120_000;

export type PostsState = 'loading' | 'ready' | 'error';

/** What a retry ended with: the post is queued again, or the reason it was refused. */
export type RetryOutcome = { ok: true } | { ok: false; failure: RetryFailure };

/**
 * Where the listings of "My listings" were posted (SOC-06): the posts of the cards shown, read
 * after the cards, then read again while one is "in progress", only while the tab of the browser
 * is visible, and never after the page is left.
 *
 * A failed read hides the pills without touching the list; a failed read again keeps the pills
 * shown.
 */
@Injectable()
export class SocialPostsStore {
  private readonly api = inject(SocialApi);
  private readonly document = inject(DOCUMENT);

  private readonly posts = signal<ReadonlyMap<string, PostStatusView[]>>(new Map());
  readonly state = signal<PostsState>('loading');
  /** The post being queued again, as `listingId:network`. */
  readonly retrying = signal<string | null>(null);
  readonly hasPending = computed(() =>
    [...this.posts().values()].some((posts) => posts.some((post) => post.badge === 'PENDING')),
  );

  private ids: string[] = [];
  /** Incremented by each read, so that an answer to an older one is dropped. */
  private request = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  /** End of the polling window in milliseconds, 0 when none is open. */
  private pollUntil = 0;
  private destroyed = false;

  constructor() {
    const onVisibility = () => this.visibilityChanged();
    this.document.addEventListener('visibilitychange', onVisibility);
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.document.removeEventListener('visibilitychange', onVisibility);
      this.clearTimer();
    });
  }

  /** The posts of a listing, empty when it has none. */
  of(listingId: string): PostStatusView[] {
    return this.posts().get(listingId) ?? [];
  }

  /**
   * Reads the posts of the cards shown. The same cards are read again quietly (after an action on
   * the page); other cards start with skeletons and a new polling window.
   */
  async load(ids: readonly string[]): Promise<void> {
    const same = ids.length === this.ids.length && ids.every((id, i) => id === this.ids[i]);
    if (!same) {
      this.ids = [...ids];
      this.pollUntil = 0;
      this.clearTimer();
      if (!ids.every((id) => this.posts().has(id))) {
        this.state.set('loading');
      }
    }
    await this.read();
  }

  async retry(listingId: string, network: SocialNetwork): Promise<RetryOutcome> {
    this.retrying.set(`${listingId}:${network}`);
    try {
      const post = await this.api.retryPost(listingId, network);
      this.replace(listingId, post);
      this.pollUntil = Date.now() + POSTS_POLL_WINDOW_MS;
      this.schedule();
      return { ok: true };
    } catch (error) {
      // The post may have changed meanwhile (published, limit reached): show it as it is now.
      void this.read();
      return { ok: false, failure: error instanceof RetryError ? error.failure : 'unknown' };
    } finally {
      this.retrying.set(null);
    }
  }

  private async read(): Promise<void> {
    const request = ++this.request;
    const ids = this.ids;
    if (ids.length === 0) {
      this.posts.set(new Map());
      this.state.set('ready');
      return;
    }
    try {
      const listings = await this.api.posts(ids);
      if (request !== this.request || this.destroyed) {
        return;
      }
      this.posts.set(new Map(listings.map((listing) => [listing.listingId, listing.posts])));
      this.state.set('ready');
    } catch {
      if (request === this.request && this.state() !== 'ready') {
        this.state.set('error');
      }
    }
    if (request === this.request) {
      this.schedule();
    }
  }

  /** Plans the next read while a post is in progress, inside the window, the tab visible. */
  private schedule(): void {
    this.clearTimer();
    if (this.destroyed || !this.hasPending()) {
      this.pollUntil = 0;
      return;
    }
    if (this.pollUntil === 0) {
      this.pollUntil = Date.now() + POSTS_POLL_WINDOW_MS;
    }
    if (this.document.hidden || Date.now() + POSTS_POLL_INTERVAL_MS > this.pollUntil) {
      return;
    }
    this.timer = setTimeout(() => {
      this.timer = null;
      void this.read();
    }, POSTS_POLL_INTERVAL_MS);
  }

  /** Hidden: no more reads. Visible again inside the window: read at once. */
  private visibilityChanged(): void {
    if (this.document.hidden) {
      this.clearTimer();
    } else if (this.hasPending() && Date.now() < this.pollUntil && this.timer === null) {
      void this.read();
    }
  }

  private replace(listingId: string, post: PostStatusView): void {
    this.posts.update((all) => {
      const next = new Map(all);
      const posts = next.get(listingId) ?? [];
      next.set(
        listingId,
        posts.map((current) => (current.network === post.network ? post : current)),
      );
      return next;
    });
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
