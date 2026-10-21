import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_PREFIX, problemCode } from '@soukly/auth';
import { firstValueFrom } from 'rxjs';
import { Listing, PublicListing } from './models';

/** What `GET /listings/{id}` answered (CATA-03). */
export type ListingReading =
  | { kind: 'public'; listing: PublicListing }
  | { kind: 'owner'; listing: Listing }
  /** `410`: expired or withdrawn; only its subcategory and governorate, for similar listings. */
  | { kind: 'gone'; categoryCode: string | null; governorateCode: string | null }
  | { kind: 'not-found' };

/**
 * The listing page of the catalogue (CATA-03): one listing as anyone reads it, and the phone
 * number of its seller. Relative `/api` URLs, so the access token goes along when there is one.
 */
@Injectable({ providedIn: 'root' })
export class ListingPageApi {
  private readonly http = inject(HttpClient);

  /**
   * Reads a listing; `owner` when the signed-in member owns it (any status). Rejects with the
   * `HttpErrorResponse` of a network error or of another status (`503`).
   */
  async read(id: string): Promise<ListingReading> {
    try {
      const listing = await firstValueFrom(
        this.http.get<PublicListing | Listing>(`${API_PREFIX}/listings/${encodeURIComponent(id)}`),
      );
      return 'version' in listing
        ? { kind: 'owner', listing }
        : { kind: 'public', listing: listing as PublicListing };
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        return { kind: 'not-found' };
      }
      if (error instanceof HttpErrorResponse && error.status === 410) {
        const body = (error.error ?? {}) as Record<string, unknown>;
        return {
          kind: 'gone',
          categoryCode: typeof body['categoryCode'] === 'string' ? body['categoryCode'] : null,
          governorateCode:
            typeof body['governorateCode'] === 'string' ? body['governorateCode'] : null,
        };
      }
      throw error;
    }
  }

  /**
   * The phone number of the seller, E.164 (`+21620123456`), for a signed-in member. Rejects with
   * the `HttpErrorResponse`: `401`, `409 own-listing`, `410`, `429 contact-limit-reached` (with
   * `Retry-After`). A `POST`: counted, never cached.
   */
  async contact(id: string): Promise<string> {
    const answer = await firstValueFrom(
      this.http.post<{ phone: string }>(
        `${API_PREFIX}/listings/${encodeURIComponent(id)}/contact`,
        null,
      ),
    );
    return answer.phone;
  }
}

/** Whether an error of {@link ListingPageApi.contact} is the daily limit. */
export function isContactLimit(error: unknown): boolean {
  return (
    (error instanceof HttpErrorResponse && error.status === 429) ||
    problemCode(error) === 'contact-limit-reached'
  );
}
