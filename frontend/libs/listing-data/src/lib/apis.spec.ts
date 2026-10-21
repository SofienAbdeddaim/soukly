import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpEventType } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService, authInterceptor, sessionResponse } from '@soukly/auth';
import { ListingsApi, MERGE_PATCH_JSON } from './listings-api';
import { PhotosApi } from './photos-api';
import { ReferentialsApi } from './referentials-api';
import { SearchApi } from './search-api';
import { draftListing, TEST_FAMILIES, TEST_ID } from './testing';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('listing-data APIs', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const login = TestBed.inject(AuthService).login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
    await login;
  });

  afterEach(() => http.verify());

  describe('ListingsApi', () => {
    it('creates a draft and keeps the ETag of its version', async () => {
      const created = TestBed.inject(ListingsApi).create('vehicles.cars');
      const request = http.expectOne('/api/listings');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ categoryCode: 'vehicles.cars' });
      expect(request.request.headers.get('Authorization')).toBe('Bearer token-1');
      request.flush(draftListing(), {
        status: 201,
        statusText: 'Created',
        headers: { ETag: '"0"' },
      });

      expect(await created).toEqual({ listing: draftListing(), etag: '"0"' });
    });

    it('sends a Merge Patch with If-Match', async () => {
      const updated = TestBed.inject(ListingsApi).update(TEST_ID, '"3"', { title: 'Peugeot 208' });
      const request = http.expectOne(`/api/listings/${TEST_ID}`);
      expect(request.request.method).toBe('PATCH');
      expect(request.request.headers.get('Content-Type')).toBe(MERGE_PATCH_JSON);
      expect(request.request.headers.get('If-Match')).toBe('"3"');
      request.flush(draftListing({ version: 4, title: 'Peugeot 208' }));

      // No ETag header readable: the one listing-service derives from the version.
      expect((await updated).etag).toBe('"4"');
    });
  });

  describe('PhotosApi', () => {
    it('uploads to the presigned URL without token, cookie or interceptor', async () => {
      const progress: number[] = [];
      const file = new Blob([new Uint8Array(1000)], { type: 'image/jpeg' });
      let done = false;
      TestBed.inject(PhotosApi)
        .upload(
          {
            photoId: 'p1',
            uploadUrl: 'http://storage.test/incoming/p1?X-Amz-Signature=abc',
            method: 'PUT',
            headers: { 'Content-Type': 'image/jpeg' },
            expiresAt: '2026-10-21T09:00:00Z',
          },
          file,
        )
        .subscribe({ next: (value) => progress.push(value), complete: () => (done = true) });

      const request = http.expectOne('http://storage.test/incoming/p1?X-Amz-Signature=abc');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toBe(file);
      expect(request.request.headers.get('Content-Type')).toBe('image/jpeg');
      expect(request.request.headers.has('Authorization')).toBe(false);
      expect(request.request.headers.has('Accept-Language')).toBe(false);
      expect(request.request.withCredentials).toBe(false);
      request.event({ type: HttpEventType.UploadProgress, loaded: 500, total: 1000 });
      request.flush(null);

      expect(progress).toEqual([50, 100]);
      expect(done).toBe(true);
    });

    it('asks for an upload with the type and size of the file', async () => {
      const ticket = TestBed.inject(PhotosApi).requestUpload(TEST_ID, 'image/jpeg', 1234);
      const request = http.expectOne(`/api/listings/${TEST_ID}/photos`);
      expect(request.request.body).toEqual({ contentType: 'image/jpeg', sizeBytes: 1234 });
      request.flush({ photoId: 'p1' });
      expect((await ticket).photoId).toBe('p1');
    });
  });

  describe('ReferentialsApi', () => {
    it('downloads the categories once', async () => {
      const api = TestBed.inject(ReferentialsApi);
      const first = api.categories();
      const second = api.categories();
      http.expectOne('/api/categories').flush(TEST_FAMILIES);

      expect(await first).toEqual(TEST_FAMILIES);
      expect(await second).toBe(await first);
    });

    it('tries again after a failure', async () => {
      const api = TestBed.inject(ReferentialsApi);
      const failed = api.governorates();
      http.expectOne('/api/locations/governorates').flush(null, { status: 503, statusText: 'x' });
      await expect(failed).rejects.toBeTruthy();
      await settle();

      const again = api.governorates();
      http.expectOne('/api/locations/governorates').flush([]);
      expect(await again).toEqual([]);
    });
  });

  describe('SearchApi', () => {
    it('sends the criteria as query parameters, without the empty ones', () => {
      let total: number | undefined;
      TestBed.inject(SearchApi)
        .search({ q: 'peugeot', 'attr.fuel': 'diesel', governorate: null, priceMin: '', limit: 1 })
        .subscribe((page) => (total = page.total));
      const request = http.expectOne((candidate) => candidate.url === '/api/listings');
      expect(request.request.params.keys()).toEqual(['q', 'attr.fuel', 'limit']);
      expect(request.request.params.get('attr.fuel')).toBe('diesel');
      request.flush({ items: [], total: 31 });
      expect(total).toBe(31);
    });
  });
});
