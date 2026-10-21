import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LocaleService } from '@soukly/i18n';
import { GOVERNORATE_STORAGE_KEY } from '../search/governorate-memory';
import { card, catalogI18n, catalogProviders, FakeSearchApi } from '../testing/catalog-testing';
import { HomePage } from './home-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('HomePage', () => {
  let api: FakeSearchApi;

  async function open() {
    api = new FakeSearchApi();
    TestBed.configureTestingModule({
      imports: [catalogI18n()],
      providers: [
        provideRouter([
          { path: ':lang', component: HomePage },
          { path: ':lang/search', component: HomePage },
        ]),
        ...catalogProviders(api),
      ],
    });
    TestBed.inject(LocaleService).use('fr');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/fr', HomePage);
    await settle();
    harness.detectChanges();
    return { harness, element: harness.routeNativeElement as HTMLElement };
  }

  afterEach(() => localStorage.clear());

  it('shows the search, the families and the 20 latest listings of all of Tunisia', async () => {
    const { harness, element } = await open();

    expect(element.querySelector('form[role="search"]')).not.toBeNull();
    const families = Array.from(element.querySelectorAll('[data-testid="families"] a'));
    expect(families.map((link) => link.getAttribute('href'))).toEqual([
      '/fr/c/vehicles',
      '/fr/c/real-estate',
      '/fr/c/services',
    ]);
    expect(api.last().params).toEqual({ governorate: null, sort: 'recent', limit: 20 });

    api.respond({ items: [card(1), card(2, { priceMode: 'ON_QUOTE', price: null })], total: 2 });
    harness.detectChanges();
    // The labels of the attributes and of the delegation arrive after the cards.
    await settle();
    harness.detectChanges();
    const rows = element.querySelectorAll('ui-listing-row');
    expect(rows).toHaveLength(2);
    expect(rows[0].querySelector('bdi')?.textContent).toMatch(/^32\s000\sDT$/u);
    expect(rows[0].querySelector('.row__summary')?.textContent).toMatch(
      /^2019 · 62\s000 km · Diesel$/u,
    );
    expect(rows[0].querySelector('.row__meta')?.textContent).toContain('Sfax, Sakiet Ezzit');
    expect(rows[1].querySelector('bdi')?.textContent).toBe('Sur devis');
    expect(TestBed.inject(Title).getTitle()).toBe('Soukly · Petites annonces en Tunisie');
  });

  it('shows the latest listings of the remembered governorate, then of all of Tunisia', async () => {
    localStorage.setItem(GOVERNORATE_STORAGE_KEY, 'sfax');
    const { harness, element } = await open();

    expect(api.last().params['governorate']).toBe('sfax');
    const picker = element.querySelector<HTMLSelectElement>(
      '[data-testid="home-governorate"]',
    ) as HTMLSelectElement;
    expect(picker.selectedOptions[0].textContent?.trim()).toBe('Sfax');
    expect(element.querySelector('[data-testid="see-all"]')?.getAttribute('href')).toBe(
      '/fr/search?governorate=sfax',
    );

    picker.value = '';
    picker.dispatchEvent(new Event('change'));
    harness.detectChanges();
    expect(api.last().params['governorate']).toBeNull();
    expect(localStorage.getItem(GOVERNORATE_STORAGE_KEY)).toBeNull();
  });

  it('sends the search to /search with the text', async () => {
    const { element } = await open();
    const input = element.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = 'peugeot';
    input.dispatchEvent(new Event('input'));
    (element.querySelector('form[role="search"]') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );
    await settle();

    expect(TestBed.inject(Router).url).toBe('/fr/search?q=peugeot');
  });

  it('offers to try again when the latest listings cannot be read', async () => {
    const { harness, element } = await open();
    api.last().answer.error(new Error('offline'));
    harness.detectChanges();

    const retry = element.querySelector('[role="alert"] button') as HTMLButtonElement;
    expect(retry.textContent?.trim()).toBe('Réessayer');
    retry.click();
    expect(api.requests).toHaveLength(2);
  });
});
