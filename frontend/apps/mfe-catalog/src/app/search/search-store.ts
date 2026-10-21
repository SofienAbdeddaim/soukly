import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SEARCH_PAGE_SIZE, SearchApi, SearchCard, SearchPage } from '@soukly/listing-data';
import { catchError, EMPTY, map, Observable, of, Subject, switchMap } from 'rxjs';
import { apiParams, EMPTY_CRITERIA, SearchCriteria } from './search-criteria';

/**
 * `loading`: first page on its way (skeletons while nothing is shown); `error`: it failed, the
 * results already shown stay; `invalid`: the API refused the criteria (a changed shared link).
 */
export type SearchStatus = 'idle' | 'loading' | 'ready' | 'error' | 'invalid';
export type MoreStatus = 'idle' | 'loading' | 'error';

/** What a results page needs to come back exactly as it was left (back from a listing). */
export interface SearchSnapshot {
  criteria: SearchCriteria;
  items: SearchCard[];
  total: number | null;
  nextCursor: string | null;
}

type Command =
  | { kind: 'first'; criteria: SearchCriteria }
  | { kind: 'more'; criteria: SearchCriteria; cursor: string }
  | { kind: 'cancel' };

type Request = Exclude<Command, { kind: 'cancel' }>;

type Outcome =
  { command: Request; page: SearchPage } | { command: Request; error: 'error' | 'invalid' };

/**
 * Results of the search of a page, as signals. The criteria come from the URL (the page calls
 * `search` on every change); a newer search cancels the requests of the older one, so a slow
 * answer never replaces a newer list. Next pages are appended without duplicates.
 */
@Injectable()
export class SearchStore {
  private readonly api = inject(SearchApi);
  private readonly commands = new Subject<Command>();

  readonly criteria = signal<SearchCriteria>(EMPTY_CRITERIA);
  readonly items = signal<SearchCard[]>([]);
  /** Every matching listing; given by the first page only. */
  readonly total = signal<number | null>(null);
  readonly nextCursor = signal<string | null>(null);
  readonly status = signal<SearchStatus>('idle');
  readonly moreStatus = signal<MoreStatus>('idle');
  readonly hasMore = computed(() => this.nextCursor() !== null);

  constructor() {
    this.commands
      .pipe(
        switchMap((command) => this.run(command)),
        takeUntilDestroyed(),
      )
      .subscribe((outcome) => this.apply(outcome));
  }

  /** Reads the first page of the criteria. */
  search(criteria: SearchCriteria): void {
    this.criteria.set(criteria);
    this.status.set('loading');
    this.moreStatus.set('idle');
    this.commands.next({ kind: 'first', criteria });
  }

  /** Appends the next page, once the first one is there; ignored while a page is on its way. */
  loadMore(): void {
    const cursor = this.nextCursor();
    if (!cursor || this.status() !== 'ready' || this.moreStatus() === 'loading') {
      return;
    }
    this.moreStatus.set('loading');
    this.commands.next({ kind: 'more', criteria: this.criteria(), cursor });
  }

  /** Tries the request that failed again. */
  retry(): void {
    if (this.moreStatus() === 'error') {
      this.moreStatus.set('idle');
      this.loadMore();
    } else {
      this.search(this.criteria());
    }
  }

  snapshot(): SearchSnapshot {
    return {
      criteria: this.criteria(),
      items: this.items(),
      total: this.total(),
      nextCursor: this.nextCursor(),
    };
  }

  /** Shows the results kept when the page was left, without any request. */
  restore(snapshot: SearchSnapshot): void {
    this.commands.next({ kind: 'cancel' });
    this.criteria.set(snapshot.criteria);
    this.items.set(snapshot.items);
    this.total.set(snapshot.total);
    this.nextCursor.set(snapshot.nextCursor);
    this.status.set('ready');
    this.moreStatus.set('idle');
  }

  /** Number of listings of other criteria, for the "Show n listings" button of the filters. */
  count(criteria: SearchCriteria): Observable<number | null> {
    return this.api.search(apiParams(criteria, { limit: 1 })).pipe(
      map((page) => page.total ?? null),
      catchError(() => of(null)),
    );
  }

  private run(command: Command): Observable<Outcome> {
    // A restored snapshot cancels what was running, without any request.
    if (command.kind === 'cancel') {
      return EMPTY;
    }
    const extra =
      command.kind === 'more'
        ? { limit: SEARCH_PAGE_SIZE, cursor: command.cursor }
        : { limit: SEARCH_PAGE_SIZE };
    return this.api.search(apiParams(command.criteria, extra)).pipe(
      map((page): Outcome => ({ command, page })),
      catchError((error: unknown) =>
        of<Outcome>({
          command,
          error: error instanceof HttpErrorResponse && error.status === 400 ? 'invalid' : 'error',
        }),
      ),
    );
  }

  private apply(outcome: Outcome): void {
    const { command } = outcome;
    if ('error' in outcome) {
      if (command.kind === 'more') {
        this.moreStatus.set('error');
      } else {
        this.status.set(outcome.error);
        if (outcome.error === 'invalid') {
          this.items.set([]);
          this.total.set(null);
          this.nextCursor.set(null);
        }
      }
      return;
    }
    const { page } = outcome;
    if (command.kind === 'more') {
      const known = new Set(this.items().map((item) => item.id));
      this.items.update((items) => [...items, ...page.items.filter((item) => !known.has(item.id))]);
      this.moreStatus.set('idle');
    } else {
      this.items.set(page.items);
      this.total.set(page.total ?? page.items.length);
      this.status.set('ready');
    }
    this.nextCursor.set(page.nextCursor ?? null);
  }
}
