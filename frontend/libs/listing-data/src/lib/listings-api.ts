import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_PREFIX } from '@soukly/auth';
import { firstValueFrom } from 'rxjs';
import { Listing, ListingPatch, VersionedListing } from './models';

const LISTINGS = `${API_PREFIX}/listings`;

/** Media type of a draft update (listing-service accepts plain JSON too). */
export const MERGE_PATCH_JSON = 'application/merge-patch+json';

/**
 * Listings of the signed-in member (listing-service, ANN-01 and ANN-03). Every answer comes with
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
   * Applies a Merge Patch to a draft. Rejects with `400 validation-failed` (field errors),
   * `412 version-conflict` or `404` when the listing is no longer an editable draft.
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
}

/** The ETag header, or the one listing-service derives from the version (`"7"`). */
function versioned(response: HttpResponse<Listing>): VersionedListing {
  const listing = response.body as Listing;
  return { listing, etag: response.headers.get('ETag') ?? `"${listing.version}"` };
}
