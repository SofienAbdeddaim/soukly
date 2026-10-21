import {
  HttpBackend,
  HttpClient,
  HttpEventType,
  HttpHeaders,
  HttpRequest,
} from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_PREFIX } from '@soukly/auth';
import { filter, firstValueFrom, map, Observable } from 'rxjs';
import { ListingPhoto, UploadTicket } from './models';

/**
 * Photos of a draft (listing-service, ANN-02): ask for a presigned URL, upload the file straight
 * to the bucket, then ask for its completion (checks and WebP conversion on the server).
 */
@Injectable({ providedIn: 'root' })
export class PhotosApi {
  private readonly http = inject(HttpClient);
  /**
   * Client without any interceptor, for the presigned URL of the bucket: the access token, the
   * `Accept-Language` header and the cookies never leave the Soukly API.
   */
  private readonly storage = new HttpClient(inject(HttpBackend));

  /** `400 validation-failed` (type or size), `409 photo-limit-reached` beyond 10 photos. */
  requestUpload(listingId: string, contentType: string, sizeBytes: number): Promise<UploadTicket> {
    return firstValueFrom(
      this.http.post<UploadTicket>(photosUrl(listingId), { contentType, sizeBytes }),
    );
  }

  /**
   * Sends the file with the method and headers of the ticket, without credentials. Emits the
   * progress of the upload, from 0 to 100, then completes.
   */
  upload(ticket: UploadTicket, file: Blob): Observable<number> {
    const request = new HttpRequest(ticket.method, ticket.uploadUrl, file, {
      headers: new HttpHeaders(ticket.headers),
      reportProgress: true,
      withCredentials: false,
    });
    return this.storage.request(request).pipe(
      filter(
        (event) =>
          event.type === HttpEventType.UploadProgress || event.type === HttpEventType.Response,
      ),
      map((event) =>
        event.type === HttpEventType.UploadProgress
          ? Math.min(99, Math.round((100 * event.loaded) / (event.total || file.size || 1)))
          : 100,
      ),
    );
  }

  /** Checks and converts the uploaded file; `400 photo-rejected` with its `code` otherwise. */
  complete(listingId: string, photoId: string): Promise<ListingPhoto> {
    return firstValueFrom(
      this.http.post<ListingPhoto>(
        `${photosUrl(listingId)}/${encodeURIComponent(photoId)}/complete`,
        {},
      ),
    );
  }

  /** New order of every READY photo, cover first; answers the photos in that order. */
  reorder(listingId: string, photoIds: string[]): Promise<ListingPhoto[]> {
    return firstValueFrom(
      this.http.put<ListingPhoto[]>(`${photosUrl(listingId)}/order`, { photoIds }),
    );
  }

  delete(listingId: string, photoId: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`${photosUrl(listingId)}/${encodeURIComponent(photoId)}`),
    );
  }
}

function photosUrl(listingId: string): string {
  return `${API_PREFIX}/listings/${encodeURIComponent(listingId)}/photos`;
}
