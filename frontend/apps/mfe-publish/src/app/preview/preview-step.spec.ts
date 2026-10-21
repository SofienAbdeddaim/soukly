import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Lang } from '@soukly/i18n';
import {
  CAR_ATTRIBUTES,
  draftListing,
  Listing,
  SFAX_DELEGATIONS,
  TEST_GOVERNORATES,
  TEST_ID,
} from '@soukly/listing-data';
import {
  loadDraft,
  publishI18nTesting,
  publishTestingProviders,
  settle,
} from '../testing/publish-testing';
import { PreviewStep } from './preview-step';

const LISTING = `/api/listings/${TEST_ID}`;

const COMPLETE = draftListing({
  title: 'Peugeot 208 diesel, première main',
  description: 'Très bon état, entretien chez le concessionnaire, carnet à jour.',
  priceMode: 'FIXED',
  price: 32000,
  negotiable: true,
  governorateCode: 'sfax',
  delegationCode: 'sfax.sakiet-ezzit',
  attributes: {
    brand: 'Peugeot',
    year: 2019,
    'mileage-km': 62000,
    fuel: 'diesel',
    gearbox: 'manual',
  },
  photos: [
    {
      id: 'a',
      url: 'https://photos.test/a.webp',
      thumbnailUrl: 'https://photos.test/a-400.webp',
      width: 1600,
      height: 1200,
      position: 0,
    },
  ],
  missingFields: [],
});

describe('PreviewStep', () => {
  let http: HttpTestingController;

  async function setup(listing: Listing, lang: Lang = 'fr') {
    TestBed.configureTestingModule({
      imports: [publishI18nTesting(lang)],
      providers: publishTestingProviders(),
    });
    http = TestBed.inject(HttpTestingController);
    await loadDraft(listing, lang);
    const fixture = TestBed.createComponent(PreviewStep);
    await fixture.whenStable();
    http.expectOne(`/api/categories/${listing.categoryCode}/attributes`).flush(CAR_ATTRIBUTES);
    http.expectOne('/api/locations/governorates').flush(TEST_GOVERNORATES);
    if (listing.governorateCode) {
      http
        .expectOne(`/api/locations/governorates/${listing.governorateCode}/delegations`)
        .flush(SFAX_DELEGATIONS);
    }
    await settle();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const render = async () => {
      await settle();
      await fixture.whenStable();
    };
    const text = (selector: string) =>
      element.querySelector(selector)?.textContent?.replace(/\s+/gu, ' ').trim();
    const publish = element.querySelector('[data-testid=publish]') as HTMLButtonElement;
    return { fixture, element, render, text, publish };
  }

  afterEach(() => http.verify());

  it('shows the listing as it will look, with its details and the publication terms', async () => {
    const { element, text, publish } = await setup(COMPLETE);

    expect(element.querySelector('h1')?.textContent).toContain('Aperçu');
    expect(element.textContent).toContain('Étape 5 sur 5');
    expect(element.querySelector('.preview__image')?.getAttribute('src')).toBe(
      'https://photos.test/a.webp',
    );
    expect(text('.preview__title')).toBe('Peugeot 208 diesel, première main');
    expect(text('.preview__price')).toBe('32 000 DT · négociable');
    expect(text('.preview__meta')).toBe('Sakiet Ezzit, Sfax');
    expect(
      [...element.querySelectorAll('.preview__attribute')].map((line) =>
        [...line.children].map((cell) => cell.textContent?.replace(/\s+/gu, ' ').trim()).join(' '),
      ),
    ).toEqual([
      'Marque Peugeot',
      'Année 2019',
      'Kilométrage (km) 62 000',
      'Carburant Diesel',
      'Boîte de vitesses Manuelle',
    ]);
    expect(text('.preview__online')).toContain('En ligne pendant 60 jours');
    expect(element.querySelector('a[href="/fr/terms"]')?.textContent).toContain(
      'règles de publication',
    );
    expect(element.querySelector('[data-testid=missing-fields]')).toBeNull();
    expect(publish.disabled).toBe(false);
  });

  it('lists what is missing, each with a link to its step, and cannot publish', async () => {
    const { element, publish } = await setup(
      draftListing({
        ...COMPLETE,
        governorateCode: null,
        delegationCode: null,
        attributes: { brand: 'Peugeot', year: 2019, 'mileage-km': 62000, fuel: 'diesel' },
        missingFields: ['governorateCode', 'attributes.gearbox'],
      }),
    );

    const links = [...element.querySelectorAll('[data-testid=missing-fields] a')];
    expect(links.map((link) => [link.textContent?.trim(), link.getAttribute('href')])).toEqual([
      ['Gouvernorat', `/fr/publish/${TEST_ID}/price-location`],
      ['Boîte de vitesses', `/fr/publish/${TEST_ID}/details`],
    ]);
    expect(publish.disabled).toBe(true);
  });

  it('shows "on quote" instead of a price', async () => {
    const { text } = await setup(
      draftListing({ ...COMPLETE, kind: 'SERVICE', priceMode: 'ON_QUOTE', price: null }),
    );
    expect(text('.preview__price')).toBe('Sur devis');
  });

  it('publishes, then opens the confirmation page in place of the preview', async () => {
    const { render, publish } = await setup(COMPLETE);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    publish.click();
    await render();

    const request = http.expectOne(`${LISTING}/publish`);
    expect(request.request.method).toBe('POST');
    request.flush({ ...COMPLETE, status: 'PUBLISHED' });
    await render();

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'publish', TEST_ID, 'done'], {
      replaceUrl: true,
    });
  });

  it('reads the draft again when the API finds it incomplete, and shows what is missing', async () => {
    const { element, render, publish } = await setup(COMPLETE);
    publish.click();
    await render();
    http
      .expectOne(`${LISTING}/publish`)
      .flush(
        { type: 'urn:soukly:problem:listing-incomplete', missingFields: ['photos'] },
        { status: 422, statusText: 'x' },
      );
    await render();
    http
      .expectOne(LISTING)
      .flush({ ...COMPLETE, version: 1, photos: [], missingFields: ['photos'] });
    await render();

    const missing = element.querySelector('[data-testid=missing-fields]') as HTMLElement;
    expect(missing.textContent).toContain('Au moins une photo');
    expect(missing.querySelector('a')?.getAttribute('href')).toBe(`/fr/publish/${TEST_ID}/photos`);
    expect(publish.disabled).toBe(true);
  });

  it('is in Arabic, right to left', async () => {
    const { element, text } = await setup(COMPLETE, 'ar');
    expect(element.querySelector('h1')?.textContent).toContain('معاينة');
    expect(text('.preview__meta')).toBe('ساقية الزيت، صفاقس');
    expect(text('.preview__price')).toContain('د.ت.');
  });
});
