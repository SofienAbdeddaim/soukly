import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { Lang, LocaleService } from '@soukly/i18n';
import {
  draftListing,
  Listing,
  ListingPhoto,
  SFAX_DELEGATIONS,
  TEST_FAMILIES,
  TEST_GOVERNORATES,
  TEST_ID,
} from '@soukly/listing-data';
import { of } from 'rxjs';
import { AUTOSAVE_DELAY_MS } from '../details/details-section';
import { publishI18nTesting, publishTestingProviders, settle } from '../testing/publish-testing';
import { EditPage } from './edit-page';

const LISTING = `/api/listings/${TEST_ID}`;

function photo(id: string, position: number): ListingPhoto {
  return {
    id,
    url: `https://photos.example.test/${id}/1600.webp`,
    thumbnailUrl: `https://photos.example.test/${id}/400.webp`,
    width: 1600,
    height: 1200,
    position,
  };
}

/** A published home repair service: no specific field, a price, in Sfax. */
function published(overrides: Partial<Listing> = {}): Listing {
  return draftListing({
    status: 'PUBLISHED',
    version: 4,
    kind: 'SERVICE',
    categoryCode: 'services.home-repair',
    title: 'Plombier à domicile',
    description: 'Réparation de fuites, chauffe-eau et sanitaires, 7 jours sur 7.',
    priceMode: 'FIXED',
    price: 50,
    governorateCode: 'sfax',
    delegationCode: 'sfax.sfax-ville',
    photos: [photo('p1', 0)],
    missingFields: [],
    publishedAt: '2026-10-19T10:00:00Z',
    expiresAt: '2026-12-18T10:00:00Z',
    ...overrides,
  });
}

describe('EditPage', () => {
  let http: HttpTestingController;

  async function setup(listing: Listing, lang: Lang = 'fr') {
    TestBed.configureTestingModule({
      imports: [publishI18nTesting(lang)],
      providers: [
        ...publishTestingProviders(),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: listing.id })) },
        },
      ],
    });
    TestBed.inject(LocaleService).use(lang);
    http = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(EditPage);
    http.expectOne('/api/categories').flush(TEST_FAMILIES);
    http.expectOne(LISTING).flush(listing, { headers: { ETag: `"${listing.version}"` } });
    await settle();
    await fixture.whenStable();
    if (listing.status !== 'DRAFT') {
      http.expectOne(`/api/categories/${listing.categoryCode}/attributes`).flush([]);
      http.expectOne('/api/locations/governorates').flush(TEST_GOVERNORATES);
      http.expectOne('/api/locations/governorates/sfax/delegations').flush(SFAX_DELEGATIONS);
      await settle();
      await fixture.whenStable();
    }
    const element = fixture.nativeElement as HTMLElement;
    const render = async () => {
      await settle();
      await fixture.whenStable();
    };
    const type = (selector: string, value: string) => {
      const input = element.querySelector(selector) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      input.dispatchEvent(new Event('blur'));
      fixture.detectChanges();
    };
    const byTestId = (id: string) => element.querySelector(`[data-testid=${id}]`) as HTMLElement;
    return { fixture, element, render, type, byTestId, navigate };
  }

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('shows the banner, the locked category and the sections of the form', async () => {
    const { byTestId, element } = await setup(published());

    expect(element.querySelector('h1')?.textContent).toContain("Modifier l'annonce");
    expect(byTestId('edit-banner').textContent).toContain(
      'Annonce en ligne. Vos modifications seront visibles dès que vous les enregistrez.',
    );
    expect(byTestId('edit-category').textContent).toContain('(non modifiable après publication)');
    expect(element.querySelector('app-photos-section')).not.toBeNull();
    expect((element.querySelector('#details-title') as HTMLInputElement).value).toBe(
      'Plombier à domicile',
    );
  });

  it('sends nothing while the member types, then one PATCH on "Save"', async () => {
    const { type, byTestId, render, navigate } = await setup(published());
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });

    type('#details-title', 'Plombier et électricien à domicile');
    await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY_MS + 100);
    http.expectNone(LISTING);

    byTestId('edit-save').click();
    await vi.advanceTimersByTimeAsync(10);
    const request = http.expectOne(LISTING);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.headers.get('If-Match')).toBe('"4"');
    expect(request.request.body).toEqual({ title: 'Plombier et électricien à domicile' });
    request.flush(published({ version: 5, title: 'Plombier et électricien à domicile' }), {
      headers: { ETag: '"5"' },
    });
    await vi.advanceTimersByTimeAsync(10);
    vi.useRealTimers();
    await render();

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'account', 'listings'], {
      queryParams: { tab: 'online' },
    });
  });

  it('asks before leaving with changes not saved, and not without', async () => {
    const { fixture, type } = await setup(published());
    const page = fixture.componentInstance;

    expect(page.canLeave()).toBe(true);

    type('#details-title', 'Plombier et électricien à domicile');
    const leaving = page.canLeave() as Promise<boolean>;
    await fixture.whenStable();
    const dialog = document.querySelector('[data-testid=leave-dialog]') as HTMLElement;
    expect(dialog.textContent).toContain('Quitter sans enregistrer ?');
    expect(dialog.closest('[role=alertdialog]')).not.toBeNull();

    (document.querySelector('[data-testid=leave-stay]') as HTMLButtonElement).click();
    expect(await leaving).toBe(false);
  });

  it('shows the API refusal of an emptied field under it', async () => {
    const { type, byTestId, render, element } = await setup(published());

    type('#details-title', 'Plombier et électricien à domicile');
    byTestId('edit-save').click();
    await render();
    http.expectOne(LISTING).flush(
      {
        type: 'urn:soukly:problem:listing-incomplete',
        title: 'Listing incomplete',
        status: 422,
        missingFields: ['title'],
      },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    await render();

    expect(element.querySelector('ui-field')?.textContent).toContain('Indiquez un titre.');
  });

  it('shows the conflict with "Reload" on a 412', async () => {
    const { type, byTestId, render } = await setup(published());

    type('#details-title', 'Plombier et électricien à domicile');
    byTestId('edit-save').click();
    await render();
    http.expectOne(LISTING).flush(null, { status: 412, statusText: 'Precondition Failed' });
    await render();

    expect(byTestId('conflict').textContent).toContain('Cette annonce a été modifiée ailleurs.');
    expect((byTestId('edit-save') as HTMLButtonElement).disabled).toBe(true);
  });

  it('tells an expired listing that it stays offline', async () => {
    const { byTestId } = await setup(
      published({ status: 'EXPIRED', expiresAt: '2026-10-10T10:00:00Z' }),
    );

    expect(byTestId('edit-banner').textContent).toContain('Cette annonce a expiré.');
  });

  it('sends a draft to its own form', async () => {
    const { navigate } = await setup(draftListing());

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'publish', TEST_ID], { replaceUrl: true });
  });

  it('is in Arabic and right to left', async () => {
    const { element } = await setup(published(), 'ar');

    expect(element.querySelector('h1')?.textContent).toContain('تعديل الإعلان');
  });
});
