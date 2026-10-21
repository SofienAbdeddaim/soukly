import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { AuthService } from '@soukly/auth';
import {
  isContactLimit,
  Listing,
  ListingPageApi,
  ListingReading,
  PublicListing,
} from '@soukly/listing-data';

export type ListingPageStatus = 'loading' | 'ready' | 'gone' | 'not-found' | 'error';

/** State of "Show the number": the number stays in memory only, for this page. */
export type ContactState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'shown'; phone: string }
  | { kind: 'limit' }
  | { kind: 'error' };

/**
 * One listing page (CATA-03): the listing as the API answers it, whether the reader owns it, and
 * the phone number of the seller once asked. Provided by the page.
 */
@Injectable()
export class ListingPageStore {
  private readonly api = inject(ListingPageApi);
  private readonly auth = inject(AuthService);
  private readonly reading = signal<ListingReading | null>(null);
  private requested: string | null = null;
  private generation = 0;

  readonly status = signal<ListingPageStatus>('loading');
  readonly contact = signal<ContactState>({ kind: 'idle' });

  /** The listing shown: the public view, or the owner's. */
  readonly listing = computed<PublicListing | Listing | null>(() => {
    const reading = this.reading();
    return reading?.kind === 'public' || reading?.kind === 'owner' ? reading.listing : null;
  });

  /** The signed-in member owns it: no contact button. */
  readonly owned = computed(() => this.reading()?.kind === 'owner');

  /** Subcategory and governorate of a listing that is gone, for the similar listings. */
  readonly gone = computed(() => {
    const reading = this.reading();
    return reading?.kind === 'gone' ? reading : null;
  });

  async load(id: string): Promise<void> {
    this.requested = id;
    const generation = ++this.generation;
    this.status.set('loading');
    this.contact.set({ kind: 'idle' });
    // The token tells the API whether the reader is the owner: wait for the session of the visit.
    await this.auth.whenRestored();
    try {
      const reading = await this.api.read(id);
      if (generation !== this.generation) {
        return;
      }
      this.reading.set(reading);
      this.status.set(
        reading.kind === 'gone' ? 'gone' : reading.kind === 'not-found' ? 'not-found' : 'ready',
      );
    } catch {
      if (generation === this.generation) {
        this.reading.set(null);
        this.status.set('error');
      }
    }
  }

  retry(): Promise<void> {
    return this.requested ? this.load(this.requested) : Promise.resolve();
  }

  /**
   * Asks for the number of the seller; resolves true when it is shown. A listing gone meanwhile
   * turns the page into the "no longer available" one.
   */
  async reveal(): Promise<boolean> {
    const listing = this.listing();
    if (!listing || this.owned()) {
      return false;
    }
    const current = this.contact();
    if (current.kind === 'shown') {
      return true;
    }
    if (current.kind === 'loading') {
      return false;
    }
    this.contact.set({ kind: 'loading' });
    try {
      const phone = await this.api.contact(listing.id);
      this.contact.set({ kind: 'shown', phone });
      return true;
    } catch (error) {
      if (isContactLimit(error)) {
        this.contact.set({ kind: 'limit' });
      } else if (
        error instanceof HttpErrorResponse &&
        (error.status === 410 || error.status === 404)
      ) {
        await this.load(listing.id);
      } else {
        this.contact.set({ kind: 'error' });
      }
      return false;
    }
  }
}
