import { HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { problemCode, problemFieldErrors } from '@soukly/auth';
import {
  ListingPhoto,
  ListingsApi,
  MAX_PHOTOS,
  PHOTO_TYPES,
  PhotosApi,
  resizeForUpload,
  UploadTicket,
} from '@soukly/listing-data';
import { lastValueFrom, tap } from 'rxjs';
import { DraftStore } from '../draft/draft-store';
import { UploadQueue } from './upload-queue';

/** A tile of the photo step: a READY photo, or a file being uploaded or that failed to. */
export interface PhotoItem {
  key: string;
  /** Name of the local file, for the messages. */
  name: string;
  state: 'uploading' | 'ready' | 'failed';
  /** From 0 to 100 while uploading. */
  progress: number;
  /** Set once READY. */
  photo?: ListingPhoto;
  /** Object URL of the local file, shown until the photo is READY. */
  preview?: string;
}

/**
 * Message about a file that was not added, with the stable code it is translated from:
 * a code of the photo API (`photo.unsupported-format`, `photo.too-large`…), `limit`, `remove`,
 * `order` or `unexpected`.
 */
export interface PhotoNotice {
  key: string;
  name: string;
  code: string;
}

/** What is kept between the attempts of one upload, so a retry restarts where it stopped. */
interface Upload {
  file: File;
  blob?: Blob;
  ticket?: UploadTicket;
  sent?: boolean;
}

let nextKey = 0;

/**
 * Photos of the draft being edited (ANN-02 API): for each file, reduction in the browser,
 * upload request, direct upload to the storage with its progress, then completion; at most 3
 * uploads at the same time. A network failure keeps the tile with a retry; a refused file is
 * removed with a message, without stopping the others. The order of the READY photos is the
 * order of the tiles, cover first.
 */
@Injectable()
export class PhotoUploads {
  private readonly store = inject(DraftStore);
  private readonly photosApi = inject(PhotosApi);
  private readonly listingsApi = inject(ListingsApi);
  private readonly queue = new UploadQueue();
  private readonly uploads = new Map<string, Upload>();
  private listingId = '';

  readonly items = signal<PhotoItem[]>([]);
  readonly notices = signal<PhotoNotice[]>([]);
  readonly ready = computed(() => this.items().filter((item) => item.state === 'ready'));
  readonly uploading = computed(() => this.items().some((item) => item.state === 'uploading'));
  readonly full = computed(() => this.items().length >= MAX_PHOTOS);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.items().forEach((item) => revoke(item.preview)));
  }

  /** Starts from the READY photos of the draft. */
  init(listingId: string, photos: ListingPhoto[]): void {
    this.listingId = listingId;
    this.items.set(
      [...photos]
        .sort((a, b) => a.position - b.position)
        .map((photo) => ({
          key: `photo-${photo.id}`,
          name: '',
          state: 'ready',
          progress: 100,
          photo,
        })),
    );
  }

  /** Files chosen by the member: checked, then uploaded in the order chosen. */
  add(files: File[]): void {
    this.notices.set([]);
    let room = MAX_PHOTOS - this.items().length;
    for (const file of files) {
      const key = `upload-${nextKey++}`;
      if (!(PHOTO_TYPES as readonly string[]).includes(file.type)) {
        this.notify(key, file.name, 'photo.unsupported-format');
      } else if (room <= 0) {
        this.notify(key, file.name, 'limit');
      } else {
        room--;
        this.uploads.set(key, { file });
        this.items.update((items) => [
          ...items,
          { key, name: file.name, state: 'uploading', progress: 0, preview: preview(file) },
        ]);
        void this.queue.add(() => this.upload(key));
      }
    }
  }

  retry(key: string): void {
    if (this.uploads.has(key)) {
      this.patch(key, { state: 'uploading', progress: 0 });
      void this.queue.add(() => this.upload(key));
    }
  }

  async remove(key: string): Promise<void> {
    const item = this.items().find((candidate) => candidate.key === key);
    if (!item || item.state === 'uploading') {
      return;
    }
    if (item.photo) {
      try {
        await this.photosApi.delete(this.listingId, item.photo.id);
      } catch (error) {
        // A listing that was published keeps at least one photo (ANN-05)
        this.notify(
          key,
          item.name,
          problemCode(error) === 'listing-incomplete' ? 'last-photo' : 'remove',
        );
        return;
      }
    }
    this.drop(key);
    this.syncStore();
  }

  /** Moves a READY photo one place before (-1) or after (+1). */
  move(key: string, offset: -1 | 1): Promise<void> {
    const ready = this.ready();
    const from = ready.findIndex((item) => item.key === key);
    return this.reorder(from, from + offset);
  }

  /** Moves the READY photo at `from` to `to` (indexes among the READY photos) and saves the order. */
  async reorder(from: number, to: number): Promise<void> {
    const ready = [...this.ready()];
    if (from < 0 || from >= ready.length || to < 0 || to >= ready.length || from === to) {
      return;
    }
    const [moved] = ready.splice(from, 1);
    ready.splice(to, 0, moved);
    const others = this.items().filter((item) => item.state !== 'ready');
    this.items.set([...ready, ...others]);
    try {
      const photos = await this.photosApi.reorder(
        this.listingId,
        ready.map((item) => item.photo?.id as string),
      );
      this.applyOrder(photos);
    } catch {
      this.notify(moved.key, moved.name, 'order');
      await this.refresh();
    }
  }

  dismiss(key: string): void {
    this.notices.update((notices) => notices.filter((notice) => notice.key !== key));
  }

  private async upload(key: string): Promise<void> {
    const upload = this.uploads.get(key);
    if (!upload) {
      return;
    }
    const { file } = upload;
    try {
      upload.blob ??= await resizeForUpload(file);
    } catch {
      return this.reject(key, file.name, 'photo.too-large');
    }
    const blob = upload.blob;
    try {
      if (!upload.ticket || Date.parse(upload.ticket.expiresAt) <= Date.now()) {
        upload.ticket = await this.photosApi.requestUpload(
          this.listingId,
          blob.type || file.type,
          blob.size,
        );
        upload.sent = false;
      }
    } catch (error) {
      return this.failed(key, file.name, error);
    }
    const ticket = upload.ticket;
    if (!upload.sent) {
      try {
        await lastValueFrom(
          this.photosApi
            .upload(ticket, blob)
            .pipe(tap((progress) => this.patch(key, { progress }))),
        );
        upload.sent = true;
      } catch {
        return this.patch(key, { state: 'failed' });
      }
    }
    try {
      const photo = await this.photosApi.complete(this.listingId, ticket.photoId);
      this.uploads.delete(key);
      const item = this.items().find((candidate) => candidate.key === key);
      revoke(item?.preview);
      this.patch(key, { state: 'ready', progress: 100, photo, preview: undefined });
      this.sortReady();
      this.syncStore();
    } catch (error) {
      this.failed(key, file.name, error);
    }
  }

  /** An upload request or a completion failed: refused for good, or to be retried. */
  private failed(key: string, name: string, error: unknown): void {
    const code = problemCode(error);
    if (code === 'photo-limit-reached') {
      this.reject(key, name, 'limit');
    } else if (code === 'photo-rejected') {
      const reason = (error as HttpErrorResponse).error?.code;
      this.reject(key, name, typeof reason === 'string' ? reason : 'photo.processing-failed');
    } else if (code === 'validation-failed') {
      this.reject(key, name, problemFieldErrors(error)[0]?.code ?? 'unexpected');
    } else if (error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500) {
      this.reject(key, name, 'unexpected');
    } else {
      this.patch(key, { state: 'failed' });
    }
  }

  private reject(key: string, name: string, code: string): void {
    this.notify(key, name, code);
    this.drop(key);
  }

  private notify(key: string, name: string, code: string): void {
    this.notices.update((notices) => [
      ...notices.filter((notice) => notice.key !== key),
      { key, name, code },
    ]);
  }

  private drop(key: string): void {
    const item = this.items().find((candidate) => candidate.key === key);
    revoke(item?.preview);
    this.uploads.delete(key);
    this.items.update((items) => items.filter((candidate) => candidate.key !== key));
  }

  private patch(key: string, changes: Partial<PhotoItem>): void {
    this.items.update((items) =>
      items.map((item) => (item.key === key ? { ...item, ...changes } : item)),
    );
  }

  /** READY photos first, in the order of the API (a completed photo becomes the last one). */
  private sortReady(): void {
    const ready = [...this.ready()].sort(
      (a, b) => (a.photo?.position ?? 0) - (b.photo?.position ?? 0),
    );
    this.items.update((items) => [...ready, ...items.filter((item) => item.state !== 'ready')]);
  }

  private applyOrder(photos: ListingPhoto[]): void {
    const byId = new Map(photos.map((photo) => [photo.id, photo]));
    this.items.update((items) =>
      items.map((item) => {
        const photo = item.photo && byId.get(item.photo.id);
        return photo ? { ...item, photo } : item;
      }),
    );
    this.sortReady();
    this.syncStore();
  }

  /** The order was refused (a photo changed meanwhile): the READY photos of the API again. */
  private async refresh(): Promise<void> {
    try {
      const { listing } = await this.listingsApi.get(this.listingId);
      const others = this.items().filter((item) => item.state !== 'ready');
      this.init(this.listingId, listing.photos);
      this.items.update((items) => [...items, ...others]);
      this.syncStore();
    } catch {
      // Kept as shown; the next change of order tries again.
    }
  }

  private syncStore(): void {
    this.store.setPhotos(this.ready().map((item) => item.photo as ListingPhoto));
  }
}

function preview(file: File): string | undefined {
  return typeof URL.createObjectURL === 'function' ? URL.createObjectURL(file) : undefined;
}

function revoke(url: string | undefined): void {
  if (url && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(url);
  }
}
