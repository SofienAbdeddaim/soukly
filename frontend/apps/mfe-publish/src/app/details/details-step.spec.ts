import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import {
  APARTMENT_ATTRIBUTES,
  AttributeDefinition,
  CAR_ATTRIBUTES,
  draftListing,
  Listing,
  TEST_ID,
} from '@soukly/listing-data';
import { Lang } from '@soukly/i18n';
import {
  loadDraft,
  publishI18nTesting,
  publishTestingProviders,
  settle,
} from '../testing/publish-testing';
import { AUTOSAVE_DELAY_MS, controlKind, DetailsStep } from './details-step';

const LISTING = `/api/listings/${TEST_ID}`;

describe('DetailsStep', () => {
  let http: HttpTestingController;

  async function setup(listing: Listing, attributes: AttributeDefinition[], lang: Lang = 'fr') {
    TestBed.configureTestingModule({
      imports: [publishI18nTesting(lang)],
      providers: publishTestingProviders(),
    });
    http = TestBed.inject(HttpTestingController);
    await loadDraft(listing, lang);
    const fixture = TestBed.createComponent(DetailsStep);
    await fixture.whenStable();
    http.expectOne(`/api/categories/${listing.categoryCode}/attributes`).flush(attributes);
    await settle();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const render = async () => {
      await settle();
      await fixture.whenStable();
    };
    /** Types in a field and leaves it; `detectChanges` only, which also works with fake timers. */
    const type = async (selector: string, value: string) => {
      const input = element.querySelector(selector) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      input.dispatchEvent(new Event('blur'));
      fixture.detectChanges();
    };
    const labels = () =>
      [...element.querySelectorAll('[data-attribute] label, [data-attribute] legend')].map(
        (label) => label.textContent?.replace(/\s+/g, ' ').trim(),
      );
    return { fixture, element, render, type, labels };
  }

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('generates the fields of a car from the referentials', async () => {
    const { element, labels } = await setup(draftListing(), CAR_ATTRIBUTES);

    expect(labels()).toEqual([
      'Marque *',
      'Modèle (facultatif)',
      'Année *',
      'Kilométrage (km) *',
      'Carburant',
      'Boîte de vitesses',
    ]);
    expect(element.querySelector('#attribute-year')?.getAttribute('inputmode')).toBe('numeric');
    const fuel = element.querySelector('[data-attribute=fuel]') as HTMLElement;
    expect(fuel.querySelector('fieldset legend')?.textContent).toContain('Carburant');
    expect(
      [...fuel.querySelectorAll('button')].map((button) => button.getAttribute('aria-pressed')),
    ).toEqual(['false', 'false', 'false', 'false', 'false']);
  });

  it('shows the fields of an apartment and none of a car', async () => {
    const { element, labels } = await setup(
      draftListing({ categoryCode: 'real-estate.apartments' }),
      APARTMENT_ATTRIBUTES,
    );

    expect(labels()).toEqual(["Type d'offre", 'Surface (m²) *', 'Nombre de chambres *', 'Meublé']);
    expect(element.querySelector('[data-attribute=rooms] .ui-field__prefix')?.textContent).toBe(
      'S+',
    );
    expect(element.querySelector('[data-attribute=furnished] input[type=checkbox]')).not.toBeNull();
    expect(element.querySelector('#attribute-brand')).toBeNull();
  });

  it('renders an enumeration of more than 6 values as a select', () => {
    const many: AttributeDefinition = {
      ...CAR_ATTRIBUTES[4],
      values: Array.from({ length: 7 }, (_, index) => ({
        code: `v${index}`,
        label: { fr: `V${index}`, ar: `V${index}`, en: `V${index}` },
      })),
    };
    expect(controlKind(many)).toBe('select');
    expect(controlKind(CAR_ATTRIBUTES[4])).toBe('toggles');
    expect(controlKind(APARTMENT_ATTRIBUTES[3])).toBe('checkbox');
  });

  it('saves the valid fields 1.5 s after the last change, with thousands separators shown', async () => {
    const { element, fixture, type } = await setup(draftListing(), CAR_ATTRIBUTES);
    vi.useFakeTimers();
    await type('#details-title', 'Peugeot 208 diesel');
    await type('#attribute-mileage-km', '62000');
    await type('#attribute-year', '1800');
    await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY_MS - 100);
    http.expectNone(LISTING);

    await vi.advanceTimersByTimeAsync(200);
    const request = http.expectOne(LISTING);
    // The year is out of range: not sent, its error is shown.
    expect(request.request.body).toEqual({
      title: 'Peugeot 208 diesel',
      attributes: { 'mileage-km': 62000 },
    });
    expect(request.request.headers.get('If-Match')).toBe('"0"');
    request.flush(draftListing({ version: 1 }));
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect((element.querySelector('#attribute-mileage-km') as HTMLInputElement).value).toMatch(
      /^62\s000$/u,
    );
    expect(element.querySelector('[data-attribute=year]')?.textContent).toContain(
      'Valeur attendue entre 1950 et 2027.',
    );
    expect(element.querySelector('[data-testid=save-status]')?.textContent).toContain(
      'Brouillon enregistré',
    );
  });

  it('shows the errors of the API under their field', async () => {
    const { element, render, type } = await setup(draftListing(), CAR_ATTRIBUTES);
    await type('#attribute-brand', 'Peugeot');
    (element.querySelector('[data-testid=continue]') as HTMLButtonElement).click();
    await render();
    // Title and description are required to go on: nothing is sent while they are empty...
    expect(element.querySelector('#details-title')?.getAttribute('aria-invalid')).toBe('true');
    await type('#details-title', 'Peugeot 208');
    await type('#details-description', 'Très bon état, entretien suivi, carnet à jour.');
    await type('#attribute-year', '2019');
    await type('#attribute-mileage-km', '62000');
    for (const value of ['Diesel', 'Manuelle']) {
      [...element.querySelectorAll('[data-attribute] button')]
        .find((button) => button.textContent?.trim() === value)
        ?.dispatchEvent(new Event('click'));
    }
    (element.querySelector('[data-testid=continue]') as HTMLButtonElement).click();
    await render();

    // ...then the API refuses the brand (it knows a rule the form does not).
    http.expectOne(LISTING).flush(
      {
        type: 'urn:soukly:problem:validation-failed',
        errors: [{ field: 'attributes.brand', code: 'attribute.invalid-value' }],
      },
      { status: 400, statusText: 'x' },
    );
    await render();
    expect(element.querySelector('[data-attribute=brand]')?.textContent).toContain(
      "Cette valeur n'est pas valide.",
    );
  });

  it('goes on to "Price and location" once everything is saved', async () => {
    const complete = draftListing({
      title: 'Peugeot 208',
      description: 'Très bon état, entretien suivi, carnet à jour.',
      attributes: {
        brand: 'Peugeot',
        year: 2019,
        'mileage-km': 62000,
        fuel: 'diesel',
        gearbox: 'manual',
      },
    });
    const { element, render } = await setup(complete, CAR_ATTRIBUTES);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');

    expect((element.querySelector('#details-title') as HTMLInputElement).value).toBe('Peugeot 208');
    (element.querySelector('[data-testid=continue]') as HTMLButtonElement).click();
    await render();

    // Nothing changed: nothing to send.
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'publish', TEST_ID, 'price-location']);
  });

  it('is in Arabic, right to left, with the numbers typed left to right', async () => {
    const { element } = await setup(draftListing(), CAR_ATTRIBUTES, 'ar');
    expect(element.querySelector('h1')?.textContent).toContain('التفاصيل');
    expect(element.textContent).toContain('الخطوة 3 من 5');
    expect(
      element.querySelector('[data-attribute=year] .ui-field__group')?.getAttribute('dir'),
    ).toBe('ltr');
    expect(element.querySelector('[data-attribute=model] label')?.textContent).toContain(
      '(اختياري)',
    );
  });
});
