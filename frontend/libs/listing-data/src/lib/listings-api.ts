import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_PREFIX } from '@soukly/auth';
import { firstValueFrom } from 'rxjs';
import {
  Listing,
  ListingPatch,
  OwnListingsPage,
  OwnListingStatus,
  VersionedListing,
} from './models';

/** Listings per page of "My listings" (ANN-05; the API allows 1 to 50). */
export const OWN_LISTINGS_PAGE_SIZE = 20;

const LISTINGS = `${API_PREFIX}/listings`;

/** Media type of a draft update (listing-service accepts plain JSON too). */
export const MERGE_PATCH_JSON = 'application/merge-patch+json';

/**
 * Listings of the signed-in member (listing-service, ANN-01, ANN-03 and ANN-05). Every answer comes with
 * the `ETag` of its version: an update sends it back in `If-Match` and fails with
 * `412 version-conflict` when the listing was changed elsewhere meanwhile.
 */
@Injectable({ providedIn: 'root' })
export class ListingsApi {
  private readonly http = inject(HttpClient);

  /** Creates a draft in a subcategory; `409 draft-limit-reached` beyond 20 drafts. */
  async create(categoryCode: string): Promise<VersionedListing> {
    return versioned(
      await firstValueFrom(
        this.http.post<Listing>(LISTINGS, { categoryCode }, { observe: 'response' }),
      ),
    );
  }

  async get(id: string): Promise<VersionedListing> {
    return versioned(
      await firstValueFrom(
        this.http.get<Listing>(`${LISTINGS}/${encodeURIComponent(id)}`, { observe: 'response' }),
      ),
    );
  }

  /**
   * Applies a Merge Patch to a listing (a draft, or a published, expired or deactivated one since
   * ANN-05). Rejects with `400 validation-failed` (field errors), `412 version-conflict`,
   * `422 listing-incomplete` when a listing that was published would miss a required field, or
   * `404` when the listing was deleted.
   */
  async update(id: string, etag: string, patch: ListingPatch): Promise<VersionedListing> {
    const headers = new HttpHeaders({ 'Content-Type': MERGE_PATCH_JSON, 'If-Match': etag });
    return versioned(
      await firstValueFrom(
        this.http.patch<Listing>(`${LISTINGS}/${encodeURIComponent(id)}`, patch, {
          headers,
          observe: 'response',
        }),
      ),
    );
  }

  /** Publishes a complete draft; `422 listing-incomplete` with its `missingFields` otherwise. */
  async publish(id: string): Promise<VersionedListing> {
    return versioned(
      await firstValueFrom(
        this.http.post<Listing>(
          `${LISTINGS}/${encodeURIComponent(id)}/publish`,
          {},
          { observe: 'response' },
        ),
      ),
    );
  }

  /**
   * One page of the listings of the member in one status, the latest changed first (ANN-05).
   * `counts` of every tab come with the first page (without `cursor`).
   */
  async mine(
    status: OwnListingStatus,
    cursor?: string,
    limit = OWN_LISTINGS_PAGE_SIZE,
  ): Promise<OwnListingsPage> {
    let params = new HttpParams().set('status', status).set('limit', limit);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return firstValueFrom(this.http.get<OwnListingsPage>(`${LISTINGS}/mine`, { params }));
  }

  /** Hides a published listing until it is renewed; `409 transition-not-allowed` otherwise. */
  async deactivate(id: string): Promise<VersionedListing> {
    return this.action(id, 'deactivate');
  }

  /**
   * Puts an expired or deactivated listing, or one that expires within 3 days, online for 60
   * days (ANN-03, ANN-05); `409 renew-not-allowed` otherwise.
   */
  async renew(id: string): Promise<VersionedListing> {
    return this.action(id, 'renew');
  }

  /** Erases a draft, or deletes any other listing for good (purged after 30 days). */
  async delete(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${LISTINGS}/${encodeURIComponent(id)}`));
  }

  private async action(id: string, name: 'deactivate' | 'renew'): Promise<VersionedListing> {
    return versioned(
      await firstValueFrom(
        this.http.post<Listing>(
          `${LISTINGS}/${encodeURIComponent(id)}/${name}`,
          {},
          { observe: 'response' },
        ),
      ),
    );
  }
}

/**
 * The ETag of the listing, derived from its version (`"7"`) as listing-service does, not read from
 * the header: the Caddy of the edge changes the ETag of a compressed answer (`"7-zstd"`), which
 * would never match the version again and fail every update with a `412`.
 */
function versioned(response: HttpResponse<Listing>): VersionedListing {
  const listing = response.body as Listing;
  return { listing, etag: `"${listing.version}"` };
}
