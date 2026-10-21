import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LocaleService } from '@soukly/i18n';
import { EMPTY_CRITERIA, SearchCriteria } from '../search/search-criteria';
import { catalogI18n, catalogProviders, FakeSearchApi } from '../testing/catalog-testing';
import { FiltersForm } from './filters-form';

@Component({
  imports: [FiltersForm],
  template: `<app-filters-form [criteria]="criteria()" (criteriaChange)="criteria.set($event)" />`,
})
class Host {
  criteria = signal<SearchCriteria>(EMPTY_CRITERIA);
}

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('FiltersForm', () => {
  async function render(criteria: SearchCriteria) {
    TestBed.configureTestingModule({
      imports: [Host, catalogI18n()],
      providers: catalogProviders(new FakeSearchApi()),
    });
    TestBed.inject(LocaleService).use('fr');
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.criteria.set(criteria);
    await fixture.whenStable();
    await settle();
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('shows the specific filters of a subcategory only', async () => {
    const { element } = await render({ ...EMPTY_CRITERIA, category: 'vehicles' });
    expect(element.querySelector('[data-attribute]')).toBeNull();

    const { element: cars } = await (async () => {
      TestBed.resetTestingModule();
      return render({ ...EMPTY_CRITERIA, category: 'vehicles.cars' });
    })();
    const codes = Array.from(cars.querySelectorAll('[data-attribute]')).map((item) =>
      item.getAttribute('data-attribute'),
    );
    // "model" is not filterable.
    expect(codes).toEqual(['brand', 'year', 'mileage-km', 'fuel', 'gearbox']);
    expect(
      cars
        .querySelector('[data-attribute="mileage-km"] legend')
        ?.textContent?.replace(/\s+/gu, ' ')
        .trim(),
    ).toBe('Kilométrage (km)');
  });

  it('chooses several fuels and drops them with another family', async () => {
    const { fixture, element } = await render({ ...EMPTY_CRITERIA, category: 'vehicles.cars' });
    const fuel = (label: string) =>
      Array.from(element.querySelectorAll('[data-attribute="fuel"] button')).find(
        (button) => button.textContent?.trim() === label,
      ) as HTMLButtonElement;
    fuel('Diesel').click();
    await fixture.whenStable();
    fuel('Essence').click();
    await fixture.whenStable();
    expect(fixture.componentInstance.criteria().attributes).toEqual({ fuel: 'petrol,diesel' });

    const family = element.querySelector('[data-testid="filter-family"]') as HTMLSelectElement;
    family.value = 'real-estate';
    family.dispatchEvent(new Event('change'));
    await settle();
    expect(fixture.componentInstance.criteria()).toMatchObject({
      category: 'real-estate',
      attributes: {},
    });
  });

  it('reads the prices typed with separators and ignores the rest', async () => {
    const { fixture, element } = await render(EMPTY_CRITERIA);
    const [min, max] = Array.from(
      element.querySelectorAll<HTMLInputElement>('fieldset input[inputmode="numeric"]'),
    );
    min.value = '10 000';
    min.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    max.value = 'cheap';
    max.dispatchEvent(new Event('change'));

    expect(fixture.componentInstance.criteria()).toMatchObject({ priceMin: 10000, priceMax: null });
    expect(max.value).toBe('');
  });

  it('offers the delegations of the governorate chosen', async () => {
    const { fixture, element } = await render(EMPTY_CRITERIA);
    expect(element.querySelector('[data-testid="filter-delegation"]')).toBeNull();
    fixture.componentInstance.criteria.set({ ...EMPTY_CRITERIA, governorate: 'sfax' });
    await fixture.whenStable();
    await settle();
    await fixture.whenStable();

    const options = Array.from(
      element.querySelectorAll('[data-testid="filter-delegation"] option'),
    ).map((option) => option.textContent?.trim());
    expect(options).toEqual(['Toutes', 'Sfax Ville', 'Sakiet Ezzit']);
  });
});
