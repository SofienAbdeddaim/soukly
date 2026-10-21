import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_PREFIX } from '@soukly/auth';
import { Observable } from 'rxjs';
import { AttributeValue, ListingKind, PriceMode } from './models';

/** Sorts of the public search (CATA-01); `relevance` needs a text query. */
export const SEARCH_SORTS = ['recent', 'relevance', 'price-asc', 'price-desc'] as const;
export type SearchSort = (typeof SEARCH_SORTS)[number];

/** Results per page by default (CATA-01: 24, at most 48). */
export const SEARCH_PAGE_SIZE = 24;

/** A listing in the search results: never any data of the seller. */
export interface SearchCard {
  id: string;
  title: string;
  kind: ListingKind;
  categoryCode: string;
  priceMode: PriceMode;
  /** Whole dinars; null "on quote". */
  price: number | null;
  currency: string;
  negotiable: boolean;
  governorateCode: string;
  delegationCode: string | null;
  /** 400 px WebP of the cover, null without photo. */
  coverThumbnailUrl: string | null;
  photoCount: number;
  /** At most 3 specific attributes, in the display order of the subcategory. */
  summary: Record<string, AttributeValue>;
  publishedAt: string;
}

/** Body of `GET /listings`: `total` only on the first page, `nextCursor` absent on the last one. */
export interface SearchPage {
  items: SearchCard[];
  nextCursor?: string;
  total?: number;
}

/**
 * Query parameters of `GET /listings`, with the names of the API: `q`, `category`, `governorate`,
 * `delegation`, `priceMin`, `priceMax`, `attr.<code>` (`attr.<code>.min`, `.max`), `sort`,
 * `limit`, `cursor`. Empty values are left out.
 */
export type SearchParams = Record<string, string | number | null | undefined>;

/**
 * Public search of listing-service (CATA-01). Observables, so that a newer search cancels the
 * request of an older one (`switchMap`). A rejected parameter answers `400 invalid-search` with
 * a stable `code` (`filter.unknown-category`, `cursor.invalid`…).
 */
@Injectable({ providedIn: 'root' })
export class SearchApi {
  private readonly http = inject(HttpClient);

  search(params: SearchParams): Observable<SearchPage> {
    let query = new HttpParams();
    for (const [name, value] of Object.entries(params)) {
      if (value !== null && value !== undefined && value !== '') {
        query = query.set(name, String(value));
      }
    }
    return this.http.get<SearchPage>(`${API_PREFIX}/listings`, { params: query });
  }
}
