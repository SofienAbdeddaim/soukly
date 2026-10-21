import {
  activeFilters,
  apiParams,
  criteriaCommands,
  criteriaFromUrl,
  criteriaQueryParams,
  EMPTY_CRITERIA,
  effectiveSort,
  sameCriteria,
  SearchCriteria,
  withCategory,
  withGovernorate,
  withoutFilter,
  withQuery,
} from './search-criteria';

const PEUGEOT: SearchCriteria = {
  q: 'peugeot',
  category: 'vehicles.cars',
  governorate: 'sfax',
  delegation: 'sfax.sakiet-ezzit',
  priceMin: 10000,
  priceMax: 40000,
  attributes: { fuel: 'diesel,petrol', 'year.min': '2015', 'year.max': '2020' },
  sort: 'price-asc',
};

describe('search criteria', () => {
  it('goes from criteria to the URL and back without loss', () => {
    const query = criteriaQueryParams(PEUGEOT);

    expect(query).toEqual({
      q: 'peugeot',
      governorate: 'sfax',
      delegation: 'sfax.sakiet-ezzit',
      priceMin: '10000',
      priceMax: '40000',
      'attr.fuel': 'diesel,petrol',
      'attr.year.max': '2020',
      'attr.year.min': '2015',
      sort: 'price-asc',
    });
    expect(criteriaCommands('fr', PEUGEOT)).toEqual(['/', 'fr', 'c', 'vehicles.cars']);
    expect(criteriaFromUrl('vehicles.cars', query)).toEqual(PEUGEOT);
  });

  it('round-trips the empty search to /search without parameters', () => {
    expect(criteriaQueryParams(EMPTY_CRITERIA)).toEqual({});
    expect(criteriaCommands('ar', EMPTY_CRITERIA)).toEqual(['/', 'ar', 'search']);
    expect(criteriaFromUrl(null, {})).toEqual(EMPTY_CRITERIA);
  });

  it('keeps an Arabic text query of a shared link', () => {
    const criteria = criteriaFromUrl(null, { q: ' سيارة ', governorate: 'tunis' });

    expect(criteria.q).toBe('سيارة');
    expect(criteria.governorate).toBe('tunis');
  });

  it('drops what the API would refuse or does not know', () => {
    const criteria = criteriaFromUrl('vehicles', {
      delegation: 'sfax.sfax-ville',
      priceMin: 'cheap',
      priceMax: '-5',
      'attr.fuel': 'diesel',
      sort: 'popular',
      utm_source: 'facebook',
    });

    expect(criteria).toEqual({ ...EMPTY_CRITERIA, category: 'vehicles' });
  });

  it('applies the default sort: relevance with a text, the most recent otherwise', () => {
    expect(effectiveSort(EMPTY_CRITERIA)).toBe('recent');
    expect(effectiveSort({ ...EMPTY_CRITERIA, q: 'vélo' })).toBe('relevance');
    expect(effectiveSort({ ...EMPTY_CRITERIA, sort: 'relevance' })).toBe('recent');
    expect(effectiveSort({ ...EMPTY_CRITERIA, sort: 'price-desc' })).toBe('price-desc');
    expect(withQuery({ ...PEUGEOT, sort: 'relevance' }, '  ').sort).toBeNull();
  });

  it('builds the parameters of the API, the category included, prices in order', () => {
    const params = apiParams({ ...PEUGEOT, priceMin: 50000 }, { limit: 1 });

    expect(params).toMatchObject({
      category: 'vehicles.cars',
      priceMin: 40000,
      priceMax: 50000,
      'attr.fuel': 'diesel,petrol',
      limit: 1,
    });
  });

  it('drops the specific filters and the delegation that no longer apply', () => {
    const cars = withCategory(PEUGEOT, 'vehicles.motorcycles', ['year']);
    expect(cars.attributes).toEqual({ 'year.min': '2015', 'year.max': '2020' });
    expect(withCategory(PEUGEOT, 'vehicles').attributes).toEqual({});
    expect(withGovernorate(PEUGEOT, 'tunis').delegation).toBeNull();
  });

  it('lists one filter per chip and removes them one by one', () => {
    const filters = activeFilters(PEUGEOT, { fuel: 'ENUM', year: 'INTEGER' });

    expect(filters).toEqual([
      { kind: 'category', code: 'vehicles.cars' },
      { kind: 'governorate', code: 'sfax' },
      { kind: 'delegation', code: 'sfax.sakiet-ezzit' },
      { kind: 'price', min: 10000, max: 40000 },
      { kind: 'enum', code: 'fuel', value: 'diesel' },
      { kind: 'enum', code: 'fuel', value: 'petrol' },
      { kind: 'range', code: 'year', min: '2015', max: '2020' },
    ]);
    expect(withoutFilter(PEUGEOT, filters[4]).attributes['fuel']).toBe('petrol');
    expect(withoutFilter(PEUGEOT, filters[6]).attributes).toEqual({ fuel: 'diesel,petrol' });
    expect(withoutFilter(PEUGEOT, filters[1])).toMatchObject({
      governorate: null,
      delegation: null,
    });
    expect(withoutFilter(PEUGEOT, filters[0])).toMatchObject({ category: null, attributes: {} });
  });

  it('compares searches whatever the order of their attributes', () => {
    const reordered = {
      ...PEUGEOT,
      attributes: { 'year.max': '2020', 'year.min': '2015', fuel: 'diesel,petrol' },
    };
    expect(sameCriteria(PEUGEOT, reordered)).toBe(true);
    expect(sameCriteria(PEUGEOT, { ...PEUGEOT, category: 'vehicles' })).toBe(false);
  });
});
