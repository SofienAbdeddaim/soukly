import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { LocaleService } from '@soukly/i18n';
import { draftListing, TEST_FAMILIES, TEST_ID } from '@soukly/listing-data';
import {
  loadDraft,
  publishI18nTesting,
  publishTestingProviders,
  settle,
} from '../testing/publish-testing';
import { CategoryStep } from './category-step';
import { NewListingPage } from './new-listing-page';

function buttons(element: HTMLElement, testId: string): HTMLButtonElement[] {
  return [...element.querySelectorAll(`[data-testid=${testId}] button`)] as HTMLButtonElement[];
}

function press(element: HTMLElement, testId: string, label: string): void {
  buttons(element, testId)
    .find((button) => button.textContent?.trim() === label)
    ?.click();
}

describe('category step', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [publishI18nTesting('fr')],
      providers: publishTestingProviders(),
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates the draft when "Véhicules" then "Voitures" are chosen, then opens the photos', async () => {
    TestBed.inject(LocaleService).use('fr');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    const fixture = TestBed.createComponent(NewListingPage);
    await fixture.whenStable();
    http.expectOne('/api/categories').flush(TEST_FAMILIES);
    await settle();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Catégorie');
    expect(element.textContent).toContain('Étape 1 sur 5');
    press(element, 'families', 'Véhicules');
    await fixture.whenStable();
    expect(buttons(element, 'subcategories').map((button) => button.textContent?.trim())).toEqual([
      'Voitures',
      'Motos et scooters',
    ]);
    press(element, 'subcategories', 'Voitures');

    const create = http.expectOne('/api/listings');
    expect(create.request.body).toEqual({ categoryCode: 'vehicles.cars' });
    create.flush(draftListing(), { status: 201, statusText: 'Created' });
    await settle();

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'publish', TEST_ID, 'category'], {
      replaceUrl: true,
    });
    expect(navigate).toHaveBeenLastCalledWith(['/', 'fr', 'publish', TEST_ID, 'photos']);
  });

  it('explains the limit of 20 drafts', async () => {
    const fixture = TestBed.createComponent(NewListingPage);
    await fixture.whenStable();
    http.expectOne('/api/categories').flush(TEST_FAMILIES);
    await settle();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    press(element, 'families', 'Immobilier');
    await fixture.whenStable();
    press(element, 'subcategories', 'Appartements');
    http
      .expectOne('/api/listings')
      .flush({ type: 'urn:soukly:problem:draft-limit-reached' }, { status: 409, statusText: 'x' });
    await settle();
    await fixture.whenStable();

    expect(element.querySelector('[data-testid=create-error]')?.textContent).toContain(
      'Vous avez déjà 20 brouillons',
    );
  });

  it('saves another subcategory of a draft at once and shows the warnings of the API', async () => {
    await loadDraft(draftListing({ attributes: { brand: 'Peugeot' } }));
    const fixture = TestBed.createComponent(CategoryStep);
    await fixture.whenStable();
    http.expectOne('/api/categories').flush(TEST_FAMILIES);
    await settle();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    // The family and the subcategory of the draft are selected.
    expect(
      buttons(element, 'subcategories').map((button) => button.getAttribute('aria-pressed')),
    ).toEqual(['true', 'false']);

    press(element, 'families', 'Immobilier');
    await fixture.whenStable();
    press(element, 'subcategories', 'Appartements');
    const patch = http.expectOne(`/api/listings/${TEST_ID}`);
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ categoryCode: 'real-estate.apartments' });
    patch.flush(
      draftListing({
        version: 1,
        categoryCode: 'real-estate.apartments',
        warnings: ['attributes-removed'],
      }),
    );
    await settle();
    await fixture.whenStable();

    expect(element.querySelector('[data-testid=category-warning]')?.textContent).toContain(
      "Les informations propres à l'ancienne catégorie ont été retirées.",
    );
  });
});
