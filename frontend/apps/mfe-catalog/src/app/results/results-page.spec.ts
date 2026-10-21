import { BreakpointObserver } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LocaleService } from '@soukly/i18n';
import { of } from 'rxjs';
import { GOVERNORATE_STORAGE_KEY } from '../search/governorate-memory';
import { card, catalogI18n, catalogProviders, FakeSearchApi } from '../testing/catalog-testing';
import { ResultsPage } from './results-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('ResultsPage', () => {
  let api: FakeSearchApi;

  async function open(url: string, options: { desktop?: boolean; lang?: 'fr' | 'ar' } = {}) {
    api = new FakeSearchApi();
    TestBed.configureTestingModule({
      imports: [catalogI18n(options.lang)],
      providers: [
        provideRouter([
          { path: ':lang/search', component: ResultsPage },
          { path: ':lang/c/:categoryCode', component: ResultsPage },
        ]),
        ...catalogProviders(api),
        ...(options.desktop
          ? [
              {
                provide: BreakpointObserver,
                useValue: { observe: () => of({ matches: true }), isMatched: () => true },
              },
            ]
          : []),
      ],
    });
    TestBed.inject(LocaleService).use(options.lang ?? 'fr');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, ResultsPage);
    await settle();
    harness.detectChanges();
    return { harness, element: harness.routeNativeElement as HTMLElement };
  }

  afterEach(() => localStorage.clear());

  const text = (element: Element | null | undefined) =>
    element?.textContent?.replace(/\s+/gu, ' ').trim() ?? '';

  it('searches the criteria of the URL and shows the chips and the context sentence', async () => {
    const { harness, element } = await open(
      '/fr/c/vehicles.cars?q=peugeot&governorate=sfax&attr.fuel=diesel',
    );

    expect(api.last().params).toMatchObject({
      q: 'peugeot',
      category: 'vehicles.cars',
      governorate: 'sfax',
      'attr.fuel': 'diesel',
      limit: 24,
    });
    expect(element.querySelector('[data-testid="skeletons"]')).not.toBeNull();
    api.respond({ items: [card(1), card(2)], total: 31 });
    harness.detectChanges();

    const chips = Array.from(element.querySelectorAll('[data-testid="filter-chips"] button'));
    expect(chips.map((chip) => text(chip))).toEqual(['Voitures', 'Sfax', 'Diesel']);
    expect(chips[2].getAttribute('aria-label')).toBe('Retirer le filtre Diesel');
    const context = element.querySelector('[data-testid="results-context"]');
    expect(context?.getAttribute('aria-live')).toBe('polite');
    expect(text(context)).toBe('31 annonces « peugeot » en Voitures, Sfax');
    expect(text(element.querySelector('[data-testid="open-filters"]'))).toBe('Filtres · 3');
    expect(
      element.querySelectorAll('ul[data-testid="listings"] > li > ui-listing-row'),
    ).toHaveLength(2);
    expect(TestBed.inject(Title).getTitle()).toBe('Voitures à Sfax · Soukly');
  });

  it('removes a chip by changing the URL', async () => {
    const { harness, element } = await open(
      '/fr/c/vehicles.cars?governorate=sfax&attr.fuel=diesel',
    );
    api.respond({ items: [card(1)], total: 1 });
    harness.detectChanges();

    const diesel = element.querySelector<HTMLButtonElement>(
      '[aria-label="Retirer le filtre Diesel"]',
    ) as HTMLButtonElement;
    diesel.click();
    await settle();

    expect(TestBed.inject(Router).url).toBe('/fr/c/vehicles.cars?governorate=sfax');
    expect(api.last().params['attr.fuel']).toBeUndefined();
  });

  it('suggests removing the price filter when nothing matches', async () => {
    const { harness, element } = await open('/fr/search?priceMax=10');
    api.respond({ items: [], total: 0 });
    harness.detectChanges();

    const empty = element.querySelector('[data-testid="results-empty"]');
    expect(text(empty?.querySelector('h2'))).toBe('Aucune annonce ne correspond');
    const suggestion = Array.from(empty?.querySelectorAll('button') ?? []).find(
      (button) => text(button) === 'Retirer le filtre Prix',
    );
    suggestion?.click();
    await settle();
    expect(TestBed.inject(Router).url).toBe('/fr/search');
  });

  it('keeps the results shown on a network error and offers to try again', async () => {
    const { harness, element } = await open('/fr/search');
    api.respond({ items: [card(1)], total: 1 });
    harness.detectChanges();
    await harness.navigateByUrl('/fr/search?governorate=sfax');
    api.last().answer.error(new Error('offline'));
    harness.detectChanges();

    expect(element.querySelector('ui-alert')).not.toBeNull();
    expect(element.querySelectorAll('ul[data-testid="listings"] > li')).toHaveLength(1);
  });

  it('applies the remembered governorate to a new search', async () => {
    localStorage.setItem(GOVERNORATE_STORAGE_KEY, 'sousse');
    await open('/fr/search');

    expect(TestBed.inject(Router).url).toBe('/fr/search?governorate=sousse');
    expect(api.requests.map((request) => request.params['governorate'])).toEqual(['sousse']);
  });

  it('remembers the governorate chosen in a filter and forgets it for all of Tunisia', async () => {
    const { harness, element } = await open('/fr/search', { desktop: true });
    api.respond({ items: [card(1)], total: 1 });
    harness.detectChanges();

    const governorate = element.querySelector<HTMLSelectElement>(
      '[data-testid="filter-governorate"]',
    ) as HTMLSelectElement;
    governorate.value = 'sfax';
    governorate.dispatchEvent(new Event('change'));
    await settle();
    expect(localStorage.getItem(GOVERNORATE_STORAGE_KEY)).toBe('sfax');
    expect(TestBed.inject(Router).url).toBe('/fr/search?governorate=sfax');

    harness.detectChanges();
    governorate.value = '';
    governorate.dispatchEvent(new Event('change'));
    await settle();
    expect(localStorage.getItem(GOVERNORATE_STORAGE_KEY)).toBeNull();
  });

  it('shows the filters in a column and the results in a grid on desktop', async () => {
    const { harness, element } = await open('/fr/search', { desktop: true });
    api.respond({ items: [card(1), card(2)], total: 2 });
    harness.detectChanges();

    expect(element.querySelector('aside app-filters-form')).not.toBeNull();
    expect(element.querySelector('[data-testid="open-filters"]')).toBeNull();
    expect(
      element.querySelectorAll('ul[data-testid="listings"] > li > ui-listing-card'),
    ).toHaveLength(2);
  });

  it('reads a shared Arabic link', async () => {
    const { harness, element } = await open('/ar/search?q=سيارة&governorate=tunis', { lang: 'ar' });
    api.respond({ items: [card(1)], total: 214 });
    harness.detectChanges();

    expect(api.last().params).toMatchObject({ q: 'سيارة', governorate: 'tunis' });
    expect(text(element.querySelector('[data-testid="results-context"]'))).toBe(
      '214 إعلانًا «سيارة» في تونس',
    );
  });
});
