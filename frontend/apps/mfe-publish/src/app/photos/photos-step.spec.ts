import { HttpEventType } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { draftListing, ListingPhoto, TEST_ID } from '@soukly/listing-data';
import { DraftStore } from '../draft/draft-store';
import {
  loadDraft,
  publishI18nTesting,
  publishTestingProviders,
  settle,
} from '../testing/publish-testing';
import { PhotosStep } from './photos-step';

const PHOTOS = `/api/listings/${TEST_ID}/photos`;

function photo(id: string, position: number): ListingPhoto {
  return {
    id,
    url: `http://photos.test/${id}.webp`,
    thumbnailUrl: `http://photos.test/${id}-400.webp`,
    width: 1600,
    height: 1200,
    position,
  };
}

describe('PhotosStep', () => {
  let http: HttpTestingController;

  async function setup(photos: ListingPhoto[] = []) {
    TestBed.configureTestingModule({
      imports: [publishI18nTesting('fr')],
      providers: publishTestingProviders(),
    });
    http = TestBed.inject(HttpTestingController);
    await loadDraft(draftListing({ photos }));
    const fixture = TestBed.createComponent(PhotosStep);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const tiles = () => [...element.querySelectorAll('[data-testid=photo-tile]')] as HTMLElement[];
    const pick = async (...files: File[]) => {
      const input = element.querySelector('[data-testid=photo-input]') as HTMLInputElement;
      Object.defineProperty(input, 'files', { value: files, configurable: true });
      input.dispatchEvent(new Event('change'));
      await settle();
      await fixture.whenStable();
    };
    const render = async () => {
      await settle();
      await fixture.whenStable();
    };
    return { fixture, element, tiles, pick, render };
  }

  afterEach(() => http.verify());

  it('uploads a valid photo with its progress and refuses a PDF without blocking it', async () => {
    const { element, tiles, pick, render } = await setup();
    expect(element.querySelector('[data-testid=photo-count]')?.textContent).toContain(
      'Photos : 0 sur 10',
    );

    await pick(
      new File([new Uint8Array(2000)], 'voiture.jpg', { type: 'image/jpeg' }),
      new File(['%PDF'], 'scan.pdf', { type: 'application/pdf' }),
    );

    expect(element.querySelector('[data-testid=photo-notice]')?.textContent).toContain(
      "Ce fichier n'est pas une image. Formats acceptés : JPEG, PNG, WebP.",
    );
    expect(element.querySelector('[data-testid=photo-notice]')?.textContent).toContain('scan.pdf');

    const request = http.expectOne(PHOTOS);
    expect(request.request.body).toEqual({ contentType: 'image/jpeg', sizeBytes: 2000 });
    request.flush({
      photoId: 'p1',
      uploadUrl: 'http://storage.test/incoming/p1?signature',
      method: 'PUT',
      headers: { 'Content-Type': 'image/jpeg' },
      expiresAt: '2999-01-01T00:00:00Z',
    });
    await render();

    const put = http.expectOne('http://storage.test/incoming/p1?signature');
    expect(put.request.headers.has('Authorization')).toBe(false);
    put.event({ type: HttpEventType.UploadProgress, loaded: 1000, total: 2000 });
    await render();
    expect(tiles()[0].querySelector('[role=progressbar]')?.getAttribute('aria-valuenow')).toBe(
      '50',
    );

    put.flush(null);
    await render();
    http.expectOne(`${PHOTOS}/p1/complete`).flush(photo('p1', 0));
    await render();

    expect(tiles()).toHaveLength(1);
    expect(tiles()[0].querySelector('img')?.getAttribute('src')).toBe(
      'http://photos.test/p1-400.webp',
    );
    expect(tiles()[0].textContent).toContain('Couverture');
    expect(TestBed.inject(DraftStore).listing()?.missingFields).not.toContain('photos');
  });

  it('keeps a photo whose upload failed, with a retry that sends it again', async () => {
    const { tiles, pick, render } = await setup();
    await pick(new File([new Uint8Array(10)], 'a.jpg', { type: 'image/jpeg' }));
    const ticket = {
      photoId: 'p1',
      uploadUrl: 'http://storage.test/p1',
      method: 'PUT',
      headers: {},
      expiresAt: '2999-01-01T00:00:00Z',
    };
    http.expectOne(PHOTOS).flush(ticket);
    await render();
    http.expectOne('http://storage.test/p1').error(new ProgressEvent('error'));
    await render();

    expect(tiles()[0].textContent).toContain("Échec de l'envoi");
    const retry = [...tiles()[0].querySelectorAll('button')].find((button) =>
      button.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    await render();

    // Same ticket: no new upload request.
    http.expectOne('http://storage.test/p1').flush(null);
    await render();
    http.expectOne(`${PHOTOS}/p1/complete`).flush(photo('p1', 0));
    await render();
    expect(tiles()[0].querySelector('img')).not.toBeNull();
  });

  it('shows a photo refused by the server with its reason', async () => {
    const { element, tiles, pick, render } = await setup();
    await pick(new File([new Uint8Array(10)], 'tiny.png', { type: 'image/png' }));
    http.expectOne(PHOTOS).flush({
      photoId: 'p1',
      uploadUrl: 'http://storage.test/p1',
      method: 'PUT',
      headers: {},
      expiresAt: '2999-01-01T00:00:00Z',
    });
    await render();
    http.expectOne('http://storage.test/p1').flush(null);
    await render();
    http
      .expectOne(`${PHOTOS}/p1/complete`)
      .flush(
        { type: 'urn:soukly:problem:photo-rejected', code: 'photo.too-small' },
        { status: 400, statusText: 'x' },
      );
    await render();

    expect(tiles()).toHaveLength(0);
    expect(element.querySelector('[data-testid=photo-notice]')?.textContent).toContain(
      'Cette photo est trop petite.',
    );
  });

  it('makes the third photo the cover with the keyboard only', async () => {
    const { element, tiles, render } = await setup([photo('a', 0), photo('b', 1), photo('c', 2)]);
    const moveBefore = (index: number) =>
      tiles()[index].querySelector<HTMLButtonElement>(
        `[aria-label="Déplacer la photo ${index + 1} avant"]`,
      ) as HTMLButtonElement;

    moveBefore(2).click();
    const first = http.expectOne(`${PHOTOS}/order`);
    expect(first.request.body).toEqual({ photoIds: ['a', 'c', 'b'] });
    first.flush([photo('a', 0), photo('c', 1), photo('b', 2)]);
    await render();
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Déplacer la photo 2 avant');

    moveBefore(1).click();
    const second = http.expectOne(`${PHOTOS}/order`);
    expect(second.request.body).toEqual({ photoIds: ['c', 'a', 'b'] });
    second.flush([photo('c', 0), photo('a', 1), photo('b', 2)]);
    await render();

    expect(tiles()[0].querySelector('img')?.getAttribute('src')).toBe(
      'http://photos.test/c-400.webp',
    );
    expect(tiles()[0].textContent).toContain('Couverture');
    expect(element.querySelector('[aria-live=polite]')?.textContent).toContain(
      'Photo déplacée en position 1.',
    );
  });

  it('does not go on without a photo', async () => {
    const { element, render } = await setup();
    (element.querySelector('[data-testid=continue]') as HTMLButtonElement).click();
    await render();
    expect(element.querySelector('[data-testid=photos-blocked]')?.textContent).toContain(
      'Ajoutez au moins une photo pour continuer.',
    );
  });

  it('hides "Add" at 10 photos', async () => {
    const { element } = await setup(
      Array.from({ length: 10 }, (_, index) => photo(`p${index}`, index)),
    );
    expect(element.querySelector('[data-testid=add-photos]')).toBeNull();
    expect(element.querySelector('[data-testid=photo-count]')?.textContent).toContain(
      'Photos : 10 sur 10',
    );
  });
});
