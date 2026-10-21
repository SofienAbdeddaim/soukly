import { HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { problemCode, problemFieldErrors, problemMissingFields } from '@soukly/auth';
import { Listing, ListingPatch, ListingPhoto, ListingsApi } from '@soukly/listing-data';

export type LoadState = 'loading' | 'ready' | 'not-found' | 'error';

/**
 * State of the automatic saving, shown under the form:
 * `saving`, `saved`, `offline` (retried on its own), `conflict` (changed elsewhere: reload),
 * `invalid` (the API refused a field, shown under it) or `error`.
 */
export type SaveState = 'idle' | 'saving' | 'saved' | 'offline' | 'conflict' | 'invalid' | 'error';

/**
 * `draft`: a draft of the form (ANN-04), saved as the member types. `edit`: a published, expired
 * or deactivated listing on its edit page (ANN-05), whose changes wait for one PATCH on "Save".
 */
export type EditMode = 'draft' | 'edit';

/** First and longest delays before saving again after a network failure. */
export const RETRY_DELAYS_MS = { first: 2_000, max: 30_000 };

/**
 * The draft being edited, shared by the steps of the form (one instance per draft page), or the
 * listing of the edit page (ANN-05).
 *
 * Saving: the changes are merged into one pending JSON Merge Patch; one request at a time is in
 * flight, with the `If-Match` of the last version answered, so that changes typed meanwhile
 * never overwrite each other. A `412` stops the saving until the member reloads the draft; a
 * network failure keeps the pending changes and tries again, ever later; field errors of the
 * API are kept by field (`title`, `attributes.year`) for the form to show.
 *
 * In `edit` mode nothing is sent while the member types: the changes stay pending (`dirty`) until
 * `commit()` sends them in one PATCH, or `discard()` drops them.
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
  /** `edit` mode: changes are waiting for `commit()`. */
  readonly dirty = signal(false);

  private mode: EditMode = 'draft';
  private listingId: string | null = null;
  private etag = '';
  private pending: ListingPatch | null = null;
  private running: Promise<boolean> | null = null;
  private retryDelay = RETRY_DELAYS_MS.first;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.cancelRetry());
  }

  /**
   * Reads the draft, or in `edit` mode a listing in any status; nothing is done when it is already
   * the one loaded.
   */
  async load(id: string, mode: EditMode = 'draft'): Promise<void> {
    if (id === this.listingId && mode === this.mode && this.loadState() === 'ready') {
      return;
    }
    this.cancelRetry();
    this.mode = mode;
    this.listingId = id;
    this.pending = null;
    this.dirty.set(false);
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
    this.dirty.set(false);
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
    if (this.mode === 'edit') {
      this.dirty.set(true);
      return Promise.resolve(true);
    }
    return this.run();
  }

  /**
   * `edit` mode: sends the pending changes in one PATCH. True once saved (or nothing to save);
   * false on a conflict, a refused field or a network failure, the changes then stay pending.
   */
  async commit(): Promise<boolean> {
    if (this.saveState() === 'conflict') {
      return false;
    }
    if (!this.pending) {
      return true;
    }
    const saved = await this.run();
    this.dirty.set(this.pending !== null);
    return saved;
  }

  /** `edit` mode: drops the changes not saved. */
  discard(): void {
    this.pending = null;
    this.dirty.set(false);
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
      // Published, expired or deactivated listings have their own edit page (ANN-05).
      if (this.mode === 'draft' && listing.status !== 'DRAFT') {
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
        if (this.mode === 'edit') {
          // Nothing is saved: the whole change waits for the next "Save", once corrected.
          this.pending = mergePatches(patch, this.pending);
          this.saveState.set(outcome);
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
      // Saved again later, with whatever was typed meanwhile; on the edit page, at the next "Save".
      this.pending = mergePatches(patch, this.pending);
      this.saveState.set('offline');
      if (this.mode === 'edit') {
        return 'offline';
      }
      this.retryTimer = setTimeout(() => {
        this.retryTimer = null;
        void this.run();
      }, this.retryDelay);
      this.retryDelay = Math.min(this.retryDelay * 2, RETRY_DELAYS_MS.max);
      return 'offline';
    }
    const fields = problemFieldErrors(error);
    if (problemCode(error) === 'listing-incomplete') {
      // A listing that was published stays complete (ANN-05): the emptied fields are required.
      fields.push(
        ...problemMissingFields(error).map((field) => ({ field, code: requiredCode(field) })),
      );
    }
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

/** Error code of a required field the API reported missing: `title.required`, `required`. */
function requiredCode(field: string): string {
  switch (field) {
    case 'title':
      return 'title.required';
    case 'description':
      return 'description.required';
    case 'priceMode':
      return 'price-mode.required';
    case 'price':
      return 'price.required';
    case 'governorateCode':
      return 'governorate.required';
    default:
      return 'required';
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
