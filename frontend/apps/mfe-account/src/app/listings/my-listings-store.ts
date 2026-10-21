import { computed, inject, Injectable, signal } from '@angular/core';
import {
  ListingsApi,
  OwnListingCard,
  OwnListingStatus,
  OwnListingsPage,
} from '@soukly/listing-data';

export type ListState = 'loading' | 'ready' | 'error';

/**
 * Listings of one tab of "My listings" (ANN-05), with the counts of every tab.
 *
 * The first page of a tab brings the counts; "See more" appends the next pages. After an action
 * (deactivate, renew, delete) the first page of the tab is read again quietly, the cards staying
 * in place meanwhile, so that the cards and the counts follow the new statuses.
 */
@Injectable()
export class MyListingsStore {
  private readonly api = inject(ListingsApi);

  readonly status = signal<OwnListingStatus>('PUBLISHED');
  readonly items = signal<OwnListingCard[]>([]);
  readonly counts = signal<Partial<Record<OwnListingStatus, number>>>({});
  readonly state = signal<ListState>('loading');
  readonly loadingMore = signal(false);
  readonly moreFailed = signal(false);
  private readonly nextCursor = signal<string | null>(null);
  readonly hasMore = computed(() => this.nextCursor() !== null);

  /** Incremented by each read, so that an answer to an older one is dropped. */
  private request = 0;

  /** Shows a tab: skeletons, then its first page. */
  async open(status: OwnListingStatus): Promise<void> {
    this.status.set(status);
    this.items.set([]);
    this.nextCursor.set(null);
    this.moreFailed.set(false);
    this.state.set('loading');
    await this.readFirst();
  }

  /** Reads the first page of the tab again, keeping the cards shown until it answers. */
  async refresh(): Promise<void> {
    await this.readFirst();
  }

  async more(): Promise<void> {
    const cursor = this.nextCursor();
    if (!cursor || this.loadingMore()) {
      return;
    }
    const request = this.request;
    this.loadingMore.set(true);
    this.moreFailed.set(false);
    try {
      const page = await this.api.mine(this.status(), cursor);
      if (request === this.request) {
        this.items.update((items) => [...items, ...page.items]);
        this.nextCursor.set(page.nextCursor ?? null);
      }
    } catch {
      if (request === this.request) {
        this.moreFailed.set(true);
      }
    } finally {
      this.loadingMore.set(false);
    }
  }

  /** Takes a card off the tab at once, before the first page is read again. */
  remove(id: string): void {
    this.items.update((items) => items.filter((item) => item.id !== id));
  }

  private async readFirst(): Promise<void> {
    const request = ++this.request;
    const status = this.status();
    let page: OwnListingsPage;
    try {
      page = await this.api.mine(status);
    } catch {
      if (request === this.request && this.state() !== 'ready') {
        this.state.set('error');
      }
      return;
    }
    if (request !== this.request) {
      return;
    }
    this.items.set(page.items);
    this.nextCursor.set(page.nextCursor ?? null);
    if (page.counts) {
      this.counts.set(page.counts);
    }
    this.state.set('ready');
  }
}
