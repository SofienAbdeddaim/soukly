import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { card, catalogProviders, FakeSearchApi } from '../testing/catalog-testing';
import { EMPTY_CRITERIA } from './search-criteria';
import { SearchStore } from './search-store';

describe('SearchStore', () => {
  let api: FakeSearchApi;
  let store: SearchStore;

  beforeEach(() => {
    api = new FakeSearchApi();
    TestBed.configureTestingModule({ providers: [SearchStore, ...catalogProviders(api)] });
    store = TestBed.inject(SearchStore);
  });

  const page = (from: number, to: number) =>
    Array.from({ length: to - from + 1 }, (_, index) => card(from + index));

  it('reads the first page with its total, then appends the next ones without duplicates', () => {
    store.search({ ...EMPTY_CRITERIA, q: 'peugeot' });
    expect(store.status()).toBe('loading');
    expect(api.last().params).toMatchObject({ q: 'peugeot', limit: 24 });
    api.respond({ items: page(1, 24), total: 60, nextCursor: 'c1' });

    expect(store.status()).toBe('ready');
    expect(store.total()).toBe(60);
    expect(store.items()).toHaveLength(24);

    store.loadMore();
    store.loadMore(); // ignored: a page is on its way
    expect(api.requests).toHaveLength(2);
    expect(api.last().params).toMatchObject({ q: 'peugeot', cursor: 'c1' });
    // A listing published meanwhile shifts the pages: #24 comes again.
    api.respond({ items: page(24, 48), nextCursor: 'c2' });

    expect(store.items()).toHaveLength(48);
    expect(new Set(store.items().map((item) => item.id)).size).toBe(48);
    expect(store.total()).toBe(60);
    expect(store.hasMore()).toBe(true);

    store.loadMore();
    api.respond({ items: page(49, 60) });
    expect(store.items()).toHaveLength(60);
    expect(store.hasMore()).toBe(false);
  });

  it('cancels the request of an older search', () => {
    store.search({ ...EMPTY_CRITERIA, q: 'peugeot' });
    const older = api.last();
    store.search({ ...EMPTY_CRITERIA, q: 'renault' });

    expect(older.answer.observed).toBe(false);
    api.respond({ items: [card(7)], total: 1 });
    expect(store.items().map((item) => item.id)).toEqual([card(7).id]);
  });

  it('keeps the results shown when the next search fails, and tries again', () => {
    store.search(EMPTY_CRITERIA);
    api.respond({ items: page(1, 3), total: 3 });
    store.search({ ...EMPTY_CRITERIA, governorate: 'sfax' });
    api.last().answer.error(new HttpErrorResponse({ status: 0 }));

    expect(store.status()).toBe('error');
    expect(store.items()).toHaveLength(3);

    store.retry();
    expect(api.last().params).toMatchObject({ governorate: 'sfax' });
    api.respond({ items: [], total: 0 });
    expect(store.status()).toBe('ready');
    expect(store.items()).toEqual([]);
  });

  it('reports criteria refused by the API', () => {
    store.search({ ...EMPTY_CRITERIA, category: 'unknown' });
    api.last().answer.error(new HttpErrorResponse({ status: 400 }));

    expect(store.status()).toBe('invalid');
  });

  it('marks a failed next page, then loads it again', () => {
    store.search(EMPTY_CRITERIA);
    api.respond({ items: page(1, 24), total: 30, nextCursor: 'c1' });
    store.loadMore();
    api.last().answer.error(new HttpErrorResponse({ status: 503 }));

    expect(store.moreStatus()).toBe('error');
    expect(store.items()).toHaveLength(24);

    store.retry();
    expect(api.last().params).toMatchObject({ cursor: 'c1' });
    api.respond({ items: page(25, 30) });
    expect(store.items()).toHaveLength(30);
  });

  it('restores a snapshot without any request and ignores the answer of the search it replaces', () => {
    store.search(EMPTY_CRITERIA);
    const pending = api.last();
    store.restore({ criteria: EMPTY_CRITERIA, items: page(1, 48), total: 60, nextCursor: 'c2' });

    expect(pending.answer.observed).toBe(false);
    expect(api.requests).toHaveLength(1);
    expect(store.items()).toHaveLength(48);
    expect(store.status()).toBe('ready');
  });

  it('counts the listings of other criteria with a one-result request', () => {
    let count: number | null | undefined;
    store.count({ ...EMPTY_CRITERIA, governorate: 'sfax' }).subscribe((value) => (count = value));

    expect(api.last().params).toMatchObject({ governorate: 'sfax', limit: 1 });
    api.respond({ items: [card(1)], total: 31 });
    expect(count).toBe(31);
  });
});
