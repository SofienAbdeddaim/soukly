import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Lang } from '@soukly/i18n';
import {
  draftListing,
  Listing,
  SFAX_DELEGATIONS,
  TEST_GOVERNORATES,
  TEST_ID,
} from '@soukly/listing-data';
import { AUTOSAVE_DELAY_MS } from '../details/details-step';
import {
  loadDraft,
  publishI18nTesting,
  publishTestingProviders,
  settle,
} from '../testing/publish-testing';
import { PriceLocationStep } from './price-location-step';

const LISTING = `/api/listings/${TEST_ID}`;
const GOVERNORATES = '/api/locations/governorates';
const SFAX = `${GOVERNORATES}/sfax/delegations`;

/** A home repair service: "on quote" is allowed. */
const SERVICE = draftListing({ kind: 'SERVICE', categoryCode: 'services.home-repair' });

describe('PriceLocationStep', () => {
  let http: HttpTestingController;

  async function setup(listing: Listing, lang: Lang = 'fr') {
    TestBed.configureTestingModule({
      imports: [publishI18nTesting(lang)],
      providers: publishTestingProviders(),
    });
    http = TestBed.inject(HttpTestingController);
    await loadDraft(listing, lang);
    const fixture = TestBed.createComponent(PriceLocationStep);
    await fixture.whenStable();
    http.expectOne(GOVERNORATES).flush(TEST_GOVERNORATES);
    if (listing.governorateCode === 'sfax') {
      http.expectOne(SFAX).flush(SFAX_DELEGATIONS);
    }
    await settle();
    await fixture.whenStable();
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
    /** Chooses an option of a select by its text. */
    const choose = (selector: string, text: string) => {
      const select = element.querySelector(selector) as HTMLSelectElement;
      select.selectedIndex = [...select.options].findIndex(
        (option) => option.textContent?.trim() === text,
      );
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };
    const button = (text: string) =>
      [...element.querySelectorAll('button')].find(
        (candidate) => candidate.textContent?.trim() === text,
      ) as HTMLButtonElement;
    return { fixture, element, render, type, choose, button };
  }

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('asks a sale for a price in dinars, with the governorates of the referentials', async () => {
    const { element } = await setup(draftListing());

    expect(element.querySelector('h1')?.textContent).toContain('Prix et lieu');
    expect(element.textContent).toContain('Étape 4 sur 5');
    expect(element.querySelector('[data-testid=price-mode]')).toBeNull();
    expect(element.querySelector('#price-amount')?.getAttribute('inputmode')).toBe('numeric');
    expect(element.querySelector('#price-amount')?.getAttribute('dir')).toBe('ltr');
    expect(element.querySelector('.ui-field__unit')?.textContent?.trim()).toBe('DT');
    expect(element.querySelector('#price-negotiable')).not.toBeNull();
    expect(
      [...(element.querySelector('#location-governorate') as HTMLSelectElement).options].map(
        (option) => option.textContent?.trim(),
      ),
    ).toEqual(['Choisissez un gouvernorat', 'Tunis', 'Ariana', 'Sfax']);
    expect(element.querySelector('label[for=location-delegation]')?.textContent).toContain(
      '(facultatif)',
    );
  });

  it('saves the price, the negotiation and the place 1.5 s after the last change', async () => {
    const { element, fixture, type, choose } = await setup(draftListing());
    vi.useFakeTimers();
    type('#price-amount', '32000');
    (element.querySelector('#price-negotiable') as HTMLInputElement).click();
    choose('#location-governorate', 'Sfax');
    http.expectOne(SFAX).flush(SFAX_DELEGATIONS);
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    choose('#location-delegation', 'Sakiet Ezzit');

    await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY_MS);
    const request = http.expectOne(LISTING);
    // A sale has a fixed price: the mode is sent with the first price.
    expect(request.request.body).toEqual({
      priceMode: 'FIXED',
      price: 32000,
      negotiable: true,
      governorateCode: 'sfax',
      delegationCode: 'sfax.sakiet-ezzit',
    });
    request.flush(draftListing({ version: 1 }));
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    expect((element.querySelector('#price-amount') as HTMLInputElement).value).toMatch(
      /^32\s000$/u,
    );
  });

  it('clears the delegation when another governorate is chosen', async () => {
    const { fixture, choose } = await setup(
      draftListing({
        version: 2,
        priceMode: 'FIXED',
        price: 32000,
        governorateCode: 'sfax',
        delegationCode: 'sfax.sakiet-ezzit',
      }),
    );
    vi.useFakeTimers();
    choose('#location-governorate', 'Tunis');
    http.expectOne(`${GOVERNORATES}/tunis/delegations`).flush([]);
    await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY_MS);
    fixture.detectChanges();

    expect(http.expectOne(LISTING).request.body).toEqual({
      governorateCode: 'tunis',
      delegationCode: null,
    });
  });

  it('lets a service choose "on quote": the price and the negotiation disappear', async () => {
    const { element, fixture, button } = await setup(SERVICE);
    const mode = element.querySelector('[data-testid=price-mode]') as HTMLElement;
    expect(mode.querySelector('legend')?.textContent).toContain('Tarif');

    vi.useFakeTimers();
    button('Sur devis').click();
    fixture.detectChanges();
    expect(element.querySelector('#price-amount')).toBeNull();
    expect(element.querySelector('#price-negotiable')).toBeNull();
    await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY_MS);
    const request = http.expectOne(LISTING);
    expect(request.request.body).toEqual({ priceMode: 'ON_QUOTE' });
    request.flush({ ...SERVICE, version: 1, priceMode: 'ON_QUOTE' });
    await vi.advanceTimersByTimeAsync(0);
  });

  it('refuses to go on without a price and a governorate, and shows why', async () => {
    const { element, render, type } = await setup(draftListing());
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    type('#price-amount', '0');
    (element.querySelector('[data-testid=continue]') as HTMLButtonElement).click();
    await render();

    expect(element.querySelector('#price-amount')?.getAttribute('aria-invalid')).toBe('true');
    expect(element.textContent?.replace(/\s+/gu, ' ')).toContain(
      'Le prix doit être compris entre 1 et 100 000 000 dinars.',
    );
    expect(element.textContent).toContain('Choisissez un gouvernorat.');
    expect(navigate).not.toHaveBeenCalled();
    expect(document.activeElement?.id).toBe('price-amount');
  });

  it('goes on to the preview once everything is saved', async () => {
    const { element, render } = await setup(
      draftListing({ priceMode: 'FIXED', price: 32000, governorateCode: 'sfax' }),
    );
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    (element.querySelector('[data-testid=continue]') as HTMLButtonElement).click();
    await render();

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'publish', TEST_ID, 'preview']);
  });

  it('is in Arabic, right to left, with the price typed left to right before "د.ت."', async () => {
    const { element } = await setup(
      draftListing({ priceMode: 'FIXED', price: 32000, governorateCode: 'sfax' }),
      'ar',
    );
    expect(element.querySelector('h1')?.textContent).toContain('السعر والمكان');
    expect(element.querySelector('.ui-field__unit')?.textContent?.trim()).toBe('د.ت.');
    expect((element.querySelector('#price-amount') as HTMLInputElement).value).toBe('32.000');
    expect(
      (element.querySelector('#location-governorate') as HTMLSelectElement).selectedOptions[0]
        .textContent,
    ).toContain('صفاقس');
  });
});
