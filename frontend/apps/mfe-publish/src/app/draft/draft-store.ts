import { HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { problemCode, problemFieldErrors } from '@soukly/auth';
import { Listing, ListingPatch, ListingPhoto, ListingsApi } from '@soukly/listing-data';

export type LoadState = 'loading' | 'ready' | 'not-found' | 'error';

/**
 * State of the automatic saving, shown under the form:
 * `saving`, `saved`, `offline` (retried on its own), `conflict` (changed elsewhere: reload),
 * `invalid` (the API refused a field, shown under it) or `error`.
 */
export type SaveState = 'idle' | 'saving' | 'saved' | 'offline' | 'conflict' | 'invalid' | 'error';

/** First and longest delays before saving again after a network failure. */
export const RETRY_DELAYS_MS = { first: 2_000, max: 30_000 };

/**
 * The draft being edited, shared by the steps of the form (one instance per draft page).
 *
 * Saving: the changes are merged into one pending JSON Merge Patch; one request at a time is in
 * flight, with the `If-Match` of the last version answered, so that changes typed meanwhile
 * never overwrite each other. A `412` stops the saving until the member reloads the draft; a
 * network failure keeps the pending changes and tries again, ever later; field errors of the
 * API are kept by field (`title`, `attributes.year`) for the form to show.
 */
@Injectable()
export class DraftStore {
  private readonly api = inject(ListingsApi);

  readonly listing = signal<Listing | null>(null);
  readonly loadState = signal<LoadState>('loading');
  readonly saveState = signal<SaveState>('idle');
  /** Error code of the API by field, from the last refused save. */
  readonly fieldErrors = signal<Readonly<Record<string, string>>>({});
  /** Incremented each time the draft is read from the API: the forms then start again from it. */
  readonly revision = signal(0);
  readonly id = computed(() => this.listing()?.id ?? null);

  private listingId: string | null = null;
  private etag = '';
  private pending: ListingPatch | null = null;
  private running: Promise<boolean> | null = null;
  private retryDelay = RETRY_DELAYS_MS.first;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.cancelRetry());
  }

  /** Reads the draft; nothing is done when it is already the one loaded. */
  async load(id: string): Promise<void> {
    if (id === this.listingId && this.loadState() === 'ready') {
      return;
    }
    this.cancelRetry();
    this.listingId = id;
    this.pending = null;
    this.listing.set(null);
    this.saveState.set('idle');
    this.fieldErrors.set({});
    this.loadState.set('loading');
    await this.read(id);
  }

  /** Reads the draft again, dropping the changes not saved yet (after a conflict). */
  async reload(): Promise<void> {
    if (!this.listingId) {
      return;
    }
    this.cancelRetry();
    this.pending = null;
    this.fieldErrors.set({});
    this.saveState.set('idle');
    await this.read(this.listingId);
  }

  /**
   * Adds changes to the pending patch and saves it. Resolves with true once everything pending
   * is saved, false when the saving stopped (conflict, refused field, network).
   */
  save(patch: ListingPatch): Promise<boolean> {
    if (this.saveState() === 'conflict') {
      return Promise.resolve(false);
    }
    this.pending = mergePatches(this.pending, patch);
    this.clearFieldErrors(patch);
    return this.run();
  }

  /** Waits for the saving in progress; true when nothing is left to save. */
  async flush(): Promise<boolean> {
    if (this.running) {
      await this.running;
    }
    return !this.pending && !['conflict', 'invalid', 'error'].includes(this.saveState());
  }

  /** The READY photos changed (ANN-02 endpoints, no new version of the listing). */
  setPhotos(photos: ListingPhoto[]): void {
    this.listing.update((listing) => {
      if (!listing) {
        return listing;
      }
      const others = listing.missingFields.filter((field) => field !== 'photos');
      return {
        ...listing,
        photos,
        missingFields: photos.length ? others : ['photos', ...others],
      };
    });
  }

  private async read(id: string): Promise<void> {
    try {
      const { listing, etag } = await this.api.get(id);
      if (id !== this.listingId) {
        return;
      }
      // Published or expired listings are edited from "My listings" (ANN-05), not here.
      if (listing.status !== 'DRAFT') {
        this.loadState.set('not-found');
        return;
      }
      this.etag = etag;
      this.listing.set(listing);
      this.loadState.set('ready');
      this.revision.update((revision) => revision + 1);
    } catch (error) {
      if (id === this.listingId) {
        this.loadState.set(problemCode(error) === 'listing-not-found' ? 'not-found' : 'error');
      }
    }
  }

  private run(): Promise<boolean> {
    if (!this.running) {
      this.running = this.drain().finally(() => (this.running = null));
    }
    return this.running;
  }

  private async drain(): Promise<boolean> {
    this.cancelRetry();
    let refused: SaveState | null = null;
    while (this.pending && this.listingId) {
      const patch = this.pending;
      this.pending = null;
      this.saveState.set('saving');
      try {
        const { listing, etag } = await this.api.update(this.listingId, this.etag, patch);
        this.etag = etag;
        this.listing.set(listing);
        this.retryDelay = RETRY_DELAYS_MS.first;
      } catch (error) {
        const outcome = this.failed(error, patch);
        if (outcome !== 'invalid' && outcome !== 'error') {
          return false;
        }
        // A refused change is not sent again (the member corrects it, which saves it again);
        // the changes typed meanwhile still are.
        refused = outcome;
      }
    }
    this.saveState.set(refused ?? 'saved');
    return refused === null;
  }

  private failed(error: unknown, patch: ListingPatch): SaveState {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 412) {
      this.pending = null;
      this.saveState.set('conflict');
      return 'conflict';
    }
    if (status === 0 || status === 502 || status === 503 || status === 504) {
      // Saved again later, with whatever was typed meanwhile.
      this.pending = mergePatches(patch, this.pending);
      this.saveState.set('offline');
      this.retryTimer = setTimeout(() => {
        this.retryTimer = null;
        void this.run();
      }, this.retryDelay);
      this.retryDelay = Math.min(this.retryDelay * 2, RETRY_DELAYS_MS.max);
      return 'offline';
    }
    const fields = problemFieldErrors(error);
    if (!fields.length) {
      return 'error';
    }
    this.fieldErrors.update((errors) => ({
      ...errors,
      ...Object.fromEntries(fields.map(({ field, code }) => [field, code])),
    }));
    return 'invalid';
  }

  private clearFieldErrors(patch: ListingPatch): void {
    const fields = Object.keys(patch).flatMap((key) =>
      key === 'attributes' && patch.attributes
        ? Object.keys(patch.attributes).map((code) => `attributes.${code}`)
        : [key],
    );
    if (fields.some((field) => field in this.fieldErrors())) {
      this.fieldErrors.update((errors) =>
        Object.fromEntries(Object.entries(errors).filter(([field]) => !fields.includes(field))),
      );
    }
  }

  private cancelRetry(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}

/** `b` applied over `a`, as two successive Merge Patches; `attributes` merged member by member. */
export function mergePatches(a: ListingPatch | null, b: ListingPatch | null): ListingPatch | null {
  if (!a || !b) {
    return a ?? b;
  }
  const merged: ListingPatch = { ...a, ...b };
  if (a.attributes && b.attributes) {
    merged.attributes = { ...a.attributes, ...b.attributes };
  }
  return merged;
}
