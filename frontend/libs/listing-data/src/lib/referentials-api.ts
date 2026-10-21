import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_PREFIX } from '@soukly/auth';
import { firstValueFrom } from 'rxjs';
import { AttributeDefinition, CategoryFamily, Delegation, Governorate } from './models';

/**
 * Categories and places (listing-service, CAT-01), public and identical for every visitor. Each
 * answer is kept in memory for the life of the page: the API changes them only with a release.
 * A failed request is forgotten, so the next call tries again.
 */
@Injectable({ providedIn: 'root' })
export class ReferentialsApi {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Promise<unknown>>();

  /** The 10 families with their subcategories, in display order. */
  categories(): Promise<CategoryFamily[]> {
    return this.cached(`${API_PREFIX}/categories`);
  }

  /** Specific attributes of a subcategory, in display order (empty for most of them). */
  attributes(categoryCode: string): Promise<AttributeDefinition[]> {
    return this.cached(`${API_PREFIX}/categories/${encodeURIComponent(categoryCode)}/attributes`);
  }

  /** The 24 governorates. */
  governorates(): Promise<Governorate[]> {
    return this.cached(`${API_PREFIX}/locations/governorates`);
  }

  delegations(governorateCode: string): Promise<Delegation[]> {
    return this.cached(
      `${API_PREFIX}/locations/governorates/${encodeURIComponent(governorateCode)}/delegations`,
    );
  }

  private cached<T>(url: string): Promise<T> {
    let answer = this.cache.get(url) as Promise<T> | undefined;
    if (!answer) {
      answer = firstValueFrom(this.http.get<T>(url));
      this.cache.set(url, answer);
      answer.catch(() => this.cache.delete(url));
    }
    return answer;
  }
}
