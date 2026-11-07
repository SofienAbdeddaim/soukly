import { DOCUMENT } from '@angular/common';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PostStatusView } from '@soukly/social-data';
import { accountTestingProviders } from '../testing/account-testing';
import { POSTS_POLL_INTERVAL_MS, SocialPostsStore } from './social-posts-store';

const LISTING = 'l-1';

function post(badge: PostStatusView['badge']): PostStatusView {
  return {
    network: 'INSTAGRAM',
    status: badge === 'PUBLISHED' ? 'PUBLISHED' : 'PENDING',
    badge,
    retryable: false,
    manualRetriesLeft: 3,
    updatedAt: '2026-10-21T09:00:00Z',
  };
}

describe('SocialPostsStore', () => {
  let http: HttpTestingController;
  let store: SocialPostsStore;
  let hidden = false;

  const flushMicrotasks = () => vi.advanceTimersByTimeAsync(0);
  const answer = (badge: PostStatusView['badge']) =>
    http
      .expectOne((request) => request.url === '/api/social/posts')
      .flush({ listings: [{ listingId: LISTING, posts: [post(badge)] }] });
  const pendingReads = () => http.match((request) => request.url === '/api/social/posts');

  beforeEach(() => {
    vi.useFakeTimers();
    hidden = false;
    TestBed.configureTestingModule({ providers: [...accountTestingProviders(), SocialPostsStore] });
    const document = TestBed.inject(DOCUMENT);
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(SocialPostsStore);
  });

  afterEach(() => {
    delete (TestBed.inject(DOCUMENT) as unknown as Record<string, unknown>)['hidden'];
    vi.useRealTimers();
    http.verify();
  });

  it('reads again every 15 seconds while a post is in progress, then stops', async () => {
    void store.load([LISTING]);
    expect(store.state()).toBe('loading');
    answer('PENDING');
    await flushMicrotasks();
    expect(store.of(LISTING)[0].badge).toBe('PENDING');

    await vi.advanceTimersByTimeAsync(POSTS_POLL_INTERVAL_MS);
    answer('PENDING');
    await flushMicrotasks();
    await vi.advanceTimersByTimeAsync(POSTS_POLL_INTERVAL_MS);
    answer('PUBLISHED');
    await flushMicrotasks();
    expect(store.of(LISTING)[0].badge).toBe('PUBLISHED');

    await vi.advanceTimersByTimeAsync(5 * POSTS_POLL_INTERVAL_MS);
    expect(pendingReads()).toHaveLength(0);
  });

  it('gives up after 2 minutes', async () => {
    void store.load([LISTING]);
    answer('PENDING');
    await flushMicrotasks();
    for (let read = 0; read < 8; read++) {
      await vi.advanceTimersByTimeAsync(POSTS_POLL_INTERVAL_MS);
      answer('PENDING');
      await flushMicrotasks();
    }
    await vi.advanceTimersByTimeAsync(4 * POSTS_POLL_INTERVAL_MS);
    expect(pendingReads()).toHaveLength(0);
  });

  it('pauses while the tab is hidden and reads at once when it is shown again', async () => {
    const document = TestBed.inject(DOCUMENT);
    void store.load([LISTING]);
    answer('PENDING');
    await flushMicrotasks();

    hidden = true;
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(3 * POSTS_POLL_INTERVAL_MS);
    expect(pendingReads()).toHaveLength(0);

    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    answer('PUBLISHED');
    await flushMicrotasks();
    expect(store.hasPending()).toBe(false);
  });

  it('stops when the page is left', async () => {
    void store.load([LISTING]);
    answer('PENDING');
    await flushMicrotasks();

    TestBed.resetTestingModule();
    await vi.advanceTimersByTimeAsync(3 * POSTS_POLL_INTERVAL_MS);
    http.verify();
  });
});
