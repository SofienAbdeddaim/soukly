import { Injectable } from '@angular/core';
import { SearchSnapshot } from './search-store';

/** A results page kept in memory, with how far it was scrolled. */
export interface KeptResults {
  snapshot: SearchSnapshot;
  scrollY: number;
}

/** Results pages kept at most, the oldest is forgotten first. */
export const MAX_KEPT_RESULTS = 5;

/**
 * Results pages the visitor left, by URL, for the life of the page: back from a listing, the list
 * comes back with every page already loaded and at the same scroll position, without any request.
 */
@Injectable({ providedIn: 'root' })
export class SearchSnapshots {
  private readonly kept = new Map<string, KeptResults>();

  keep(url: string, results: KeptResults): void {
    this.kept.delete(url);
    this.kept.set(url, results);
    while (this.kept.size > MAX_KEPT_RESULTS) {
      this.kept.delete(this.kept.keys().next().value as string);
    }
  }

  take(url: string): KeptResults | null {
    const results = this.kept.get(url) ?? null;
    this.kept.delete(url);
    return results;
  }
}
