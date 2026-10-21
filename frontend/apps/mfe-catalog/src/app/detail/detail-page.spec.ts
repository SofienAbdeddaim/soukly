import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import {
  draftListing,
  ListingPageApi,
  ListingReading,
  publicListing,
  TEST_ID,
} from '@soukly/listing-data';
import { card, catalogI18n, catalogProviders, FakeSearchApi } from '../testing/catalog-testing';
import { DetailPage, shorten } from './detail-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

const text = (element: Element | null | undefined) =>
  element?.textContent?.replace(/\s+/gu, ' ').trim() ?? '';

/** The listing API, answered by the test. */
class FakeListingPageApi {
  reading: ListingReading = { kind: 'public', listing: publicListing() };
  contactAnswer: () => Promise<string> = async () => '+21620123456';
  readonly reads: string[] = [];
  readonly contacts: string[] = [];

  async read(id: string): Promise<ListingReading> {
    this.reads.push(id);
    return this.reading;
  }

  contact(id: string): Promise<string> {
    this.contacts.push(id);
    return this.contactAnswer();
  }
}

describe('DetailPage', () => {
  let api: FakeListingPageApi;
  let search: FakeSearchApi;
  const authenticated = signal(false);

  async function open(
    url: string,
    options: { lang?: 'fr' | 'ar'; signedIn?: boolean; reading?: ListingReading } = {},
  ) {
    api = new FakeListingPageApi();
    if (options.reading) {
      api.reading = options.reading;
    }
    search = new FakeSearchApi();
    authenticated.set(options.signedIn ?? false);
    TestBed.configureTestingModule({
      imports: [catalogI18n(options.lang)],
      providers: [
        provideRouter([
          { path: ':lang/listings/:id', component: DetailPage },
          { path: ':lang/account/login', children: [] },
          { path: ':lang', children: [] },
        ]),
        ...catalogProviders(search),
        { provide: ListingPageApi, useValue: api },
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: authenticated,
            user: signal(null),
            whenRestored: async () => undefined,
          },
        },
      ],
    });
    TestBed.inject(LocaleService).use(options.lang ?? 'fr');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await settle();
    await settle();
    harness.detectChanges();
    return { harness, element: harness.routeNativeElement as HTMLElement };
  }

  afterEach(() =>
    document.querySelectorAll('.cdk-overlay-container').forEach((node) => node.remove()),
  );

  it('shows the price, place, attributes, description and seller of a listing', async () => {
    const { element } = await open(`/fr/listings/${TEST_ID}`);

    expect(api.reads).toEqual([TEST_ID]);
    expect(text(element.querySelector('h1'))).toBe('Peugeot 208 diesel');
    expect(text(element.querySelector('[data-testid="price"]'))).toMatch(/^32\s000 DT/u);
    expect(element.querySelector('[data-testid="negotiable"]')).not.toBeNull();
    expect(text(element.querySelector('[data-testid="place"]'))).toContain('Sfax, Sakiet Ezzit');
    const facts = text(element.querySelector('[data-testid="facts"]'));
    expect(facts).toContain('Peugeot');
    expect(facts).toContain('2019');
    expect(facts).toContain('Diesel');
    expect(text(element.querySelector('[data-testid="seller"]'))).toContain('Mohamed B.');
    expect(TestBed.inject(Title).getTitle()).toContain('Peugeot 208 diesel');
    expect(TestBed.inject(Meta).getTag('name="description"')?.content).toContain('Première main');
  });

  it('never reads the description as HTML', async () => {
    const { element } = await open(`/fr/listings/${TEST_ID}`, {
      reading: {
        kind: 'public',
        listing: publicListing({ description: '<img src=x onerror="alert(1)"> <b>bold</b>' }),
      },
    });

    const description = element.querySelector('[data-testid="description"]');
    expect(description?.querySelector('img, b')).toBeNull();
    expect(description?.textContent).toBe('<img src=x onerror="alert(1)"> <b>bold</b>');
  });

  it('sends a visitor to the login with a return to the contact', async () => {
    const { element } = await open(`/fr/listings/${TEST_ID}`);

    element.querySelector<HTMLButtonElement>('[data-testid="show-number"]')?.click();
    await settle();

    expect(api.contacts).toEqual([]);
    expect(TestBed.inject(Router).url).toBe(
      `/fr/account/login?returnUrl=%2Ffr%2Flistings%2F${TEST_ID}%3Fcontact%3D1`,
    );
  });

  it('shows the number of the seller to a member in a sheet', async () => {
    const { harness, element } = await open(`/fr/listings/${TEST_ID}`, { signedIn: true });

    element.querySelector<HTMLButtonElement>('[data-testid="show-number"]')?.click();
    await settle();
    harness.detectChanges();

    expect(api.contacts).toEqual([TEST_ID]);
    const sheet = document.querySelector('[data-testid="contact-sheet"]');
    expect(text(sheet?.querySelector('[data-testid="contact-phone"]'))).toBe('+216 20 123 456');
    expect(sheet?.querySelector('[data-testid="contact-call"]')?.getAttribute('href')).toBe(
      'tel:+21620123456',
    );
    expect(sheet?.querySelector('[data-testid="contact-whatsapp"]')?.getAttribute('href')).toMatch(
      /^https:\/\/wa\.me\/21620123456\?text=/u,
    );
  });

  it('shows the number on its own back from the login, then leaves the URL', async () => {
    await open(`/fr/listings/${TEST_ID}?contact=1`, { signedIn: true });
    await settle();

    expect(api.contacts).toEqual([TEST_ID]);
    expect(TestBed.inject(Router).url).toBe(`/fr/listings/${TEST_ID}`);
  });

  it('says when the daily limit of contacts is reached', async () => {
    const { harness, element } = await open(`/fr/listings/${TEST_ID}`, { signedIn: true });
    api.contactAnswer = () =>
      Promise.reject(
        new HttpErrorResponse({ status: 429, error: { code: 'contact-limit-reached' } }),
      );

    element.querySelector<HTMLButtonElement>('[data-testid="show-number"]')?.click();
    await settle();
    harness.detectChanges();

    expect(element.querySelector('[data-testid="contact-limit"]')).not.toBeNull();
    expect(document.querySelector('[data-testid="contact-sheet"]')).toBeNull();
  });

  it('shows a banner instead of the contact button to the owner', async () => {
    const { element } = await open(`/fr/listings/${TEST_ID}`, {
      signedIn: true,
      reading: {
        kind: 'owner',
        listing: draftListing({
          ...publicListing(),
          status: 'EXPIRED',
          version: 3,
          missingFields: [],
        } as never),
      },
    });

    expect(element.querySelector('[data-testid="owner-banner"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="show-number"]')).toBeNull();
  });

  it('shows similar listings when the listing is gone', async () => {
    const { harness, element } = await open(`/fr/listings/${TEST_ID}`, {
      reading: { kind: 'gone', categoryCode: 'vehicles.cars', governorateCode: 'sfax' },
    });

    expect(element.querySelector('[data-testid="listing-gone"]')).not.toBeNull();
    expect(search.last().params).toMatchObject({ category: 'vehicles.cars', governorate: 'sfax' });
    search.respond({ items: [card(1), card(2), card(3), card(4)], total: 4 });
    await settle();
    harness.detectChanges();

    expect(element.querySelectorAll('app-listing-list li')).toHaveLength(4);
    expect(element.querySelector('[data-testid="gone-see-all"]')?.getAttribute('href')).toBe(
      '/fr/c/vehicles.cars?governorate=sfax',
    );
  });

  it('completes the similar listings with the whole country', async () => {
    const { harness, element } = await open(`/fr/listings/${TEST_ID}`, {
      reading: { kind: 'gone', categoryCode: 'vehicles.cars', governorateCode: 'sfax' },
    });
    search.respond({ items: [card(1)], total: 1 });
    await settle();

    expect(search.last().params).toMatchObject({ category: 'vehicles.cars', governorate: null });
    search.respond({ items: [card(1), card(2), card(3), card(4)], total: 9 });
    await settle();
    harness.detectChanges();

    expect(element.querySelectorAll('app-listing-list li')).toHaveLength(4);
  });

  it('shows a not found page for an unknown listing', async () => {
    const { element } = await open(`/fr/listings/${TEST_ID}`, { reading: { kind: 'not-found' } });

    expect(element.querySelector('[data-testid="listing-not-found"]')).not.toBeNull();
  });

  it('writes the price in Arabic with its digits from left to right', async () => {
    const { element } = await open(`/ar/listings/${TEST_ID}`, { lang: 'ar' });

    expect(text(element.querySelector('[data-testid="price"] bdi'))).toContain('32.000');
  });
});

describe('shorten', () => {
  it('keeps a short text and cuts a long one at a word', () => {
    expect(shorten('Pneus\nneufs', 20)).toBe('Pneus neufs');
    expect(shorten('un deux trois quatre cinq', 15)).toBe('un deux trois…');
  });
});
