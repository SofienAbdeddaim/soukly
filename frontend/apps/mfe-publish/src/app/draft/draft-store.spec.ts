import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from '@soukly/auth';
import { draftListing, TEST_ID } from '@soukly/listing-data';
import { DraftStore, mergePatches, RETRY_DELAYS_MS } from './draft-store';

const URL = `/api/listings/${TEST_ID}`;
const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('DraftStore', () => {
  let store: DraftStore;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        DraftStore,
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    store = TestBed.inject(DraftStore);
    http = TestBed.inject(HttpTestingController);
    const loaded = store.load(TEST_ID);
    http.expectOne(URL).flush(draftListing({ version: 3 }), { headers: { ETag: '"3"' } });
    await loaded;
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  function answer(request: TestRequest, version: number, body: object = {}) {
    request.flush(draftListing({ version, ...body }), { headers: { ETag: `"${version}"` } });
  }

  it('loads the draft and counts the reads', () => {
    expect(store.loadState()).toBe('ready');
    expect(store.listing()?.version).toBe(3);
    expect(store.revision()).toBe(1);
  });

  it('keeps one request in flight and sends what was typed meanwhile with the new ETag', async () => {
    const first = store.save({ title: 'Peugeot' });
    const second = store.save({ title: 'Peugeot 208', attributes: { year: 2019 } });
    const third = store.save({ attributes: { fuel: 'diesel' } });

    const request = http.expectOne(URL);
    expect(request.request.headers.get('If-Match')).toBe('"3"');
    expect(request.request.body).toEqual({ title: 'Peugeot' });
    expect(store.saveState()).toBe('saving');
    answer(request, 4);
    await settle();

    const next = http.expectOne(URL);
    expect(next.request.headers.get('If-Match')).toBe('"4"');
    expect(next.request.body).toEqual({
      title: 'Peugeot 208',
      attributes: { year: 2019, fuel: 'diesel' },
    });
    answer(next, 5, { title: 'Peugeot 208' });

    expect(await Promise.all([first, second, third])).toEqual([true, true, true]);
    expect(store.saveState()).toBe('saved');
    expect(store.listing()?.title).toBe('Peugeot 208');
  });

  it('stops saving on a 412 until the draft is reloaded', async () => {
    const saved = store.save({ title: 'Peugeot 208' });
    http
      .expectOne(URL)
      .flush({ type: 'urn:soukly:problem:version-conflict' }, { status: 412, statusText: 'x' });

    expect(await saved).toBe(false);
    expect(store.saveState()).toBe('conflict');
    expect(await store.save({ title: 'Other' })).toBe(false);
    http.expectNone(URL);

    const reloaded = store.reload();
    answer(http.expectOne(URL), 6, { title: 'From the other tab' });
    await reloaded;
    expect(store.saveState()).toBe('idle');
    expect(store.listing()?.title).toBe('From the other tab');
    expect(store.revision()).toBe(2);
  });

  it('keeps the changes when offline and tries again later', async () => {
    vi.useFakeTimers();
    const saved = store.save({ title: 'Peugeot 208' });
    http.expectOne(URL).error(new ProgressEvent('error'));
    expect(await saved).toBe(false);
    expect(store.saveState()).toBe('offline');

    store.save({ description: 'Très bon état, carnet à jour.' });
    const retried = http.expectOne(URL);
    expect(retried.request.body).toEqual({
      title: 'Peugeot 208',
      description: 'Très bon état, carnet à jour.',
    });
    retried.error(new ProgressEvent('error'));
    await vi.advanceTimersByTimeAsync(0);

    await vi.advanceTimersByTimeAsync(RETRY_DELAYS_MS.first * 2);
    answer(http.expectOne(URL), 4);
    await vi.advanceTimersByTimeAsync(0);
    expect(store.saveState()).toBe('saved');
  });

  it('keeps the field errors of the API and forgets one when the field changes', async () => {
    const saved = store.save({ title: 'abc', attributes: { year: 1800 } });
    http.expectOne(URL).flush(
      {
        type: 'urn:soukly:problem:validation-failed',
        errors: [
          { field: 'title', code: 'title.too-short' },
          { field: 'attributes.year', code: 'attribute.out-of-range' },
        ],
      },
      { status: 400, statusText: 'x' },
    );

    expect(await saved).toBe(false);
    expect(store.saveState()).toBe('invalid');
    expect(store.fieldErrors()).toEqual({
      title: 'title.too-short',
      'attributes.year': 'attribute.out-of-range',
    });

    store.save({ attributes: { year: 2019 } });
    expect(store.fieldErrors()).toEqual({ title: 'title.too-short' });
    answer(http.expectOne(URL), 4);
    await settle();
  });

  it('refuses to edit a listing that is no longer a draft', async () => {
    const loaded = store.load('another-id');
    http.expectOne('/api/listings/another-id').flush(draftListing({ status: 'PUBLISHED' }));
    await loaded;
    expect(store.loadState()).toBe('not-found');
  });
});

describe('mergePatches', () => {
  it('applies the second patch over the first, attributes member by member', () => {
    expect(
      mergePatches({ title: 'a', attributes: { year: 2019 } }, { attributes: { fuel: 'diesel' } }),
    ).toEqual({ title: 'a', attributes: { year: 2019, fuel: 'diesel' } });
    expect(mergePatches({ attributes: { year: 2019 } }, { attributes: null })).toEqual({
      attributes: null,
    });
    expect(mergePatches(null, { title: 'a' })).toEqual({ title: 'a' });
  });
});
