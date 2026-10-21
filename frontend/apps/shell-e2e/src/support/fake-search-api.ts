import { Page } from '@playwright/test';
import { problem } from './fake-api';
import { PHOTOS } from './fake-listing-api';

/**
 * Fakes the public search of listing-service (CATA-01, `GET /api/listings`) over a fixed set of
 * published listings, newest first: 31 Peugeot diesel and 17 Peugeot petrol cars in Sfax, 9 Renault
 * cars and 3 cars with an Arabic title in Tunis (60 vehicles), 5 flats to rent in Sousse and 2
 * services on quote in Ariana. Filters, sorts and cursor pages follow the API; register it after
 * `fakeListingApi`, which answers the referentials.
 */
export interface FakeSearchCard {
  id: string;
  title: string;
  kind: 'SALE' | 'SERVICE';
  categoryCode: string;
  priceMode: 'FIXED' | 'ON_QUOTE';
  price: number | null;
  currency: 'TND';
  negotiable: boolean;
  governorateCode: string;
  delegationCode: string | null;
  coverThumbnailUrl: string | null;
  photoCount: number;
  summary: Record<string, string | number | boolean>;
  publishedAt: string;
  /** Every attribute, for the filters; not part of the answer. */
  attributes: Record<string, string | number | boolean>;
}

export interface FakeSearchApi {
  listings: FakeSearchCard[];
  /** Query parameters of every search received. */
  requests: URLSearchParams[];
  /** Makes the next searches fail with a network error. */
  offline: boolean;
}

const BASE_TIME = Date.parse('2026-10-21T12:00:00Z');

function listing(
  n: number,
  fields: Partial<FakeSearchCard> &
    Pick<FakeSearchCard, 'title' | 'categoryCode' | 'governorateCode'>,
): FakeSearchCard {
  const attributes = fields.attributes ?? {};
  return {
    id: `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`,
    kind: 'SALE',
    priceMode: 'FIXED',
    price: 20000 + n * 500,
    currency: 'TND',
    negotiable: false,
    delegationCode: null,
    coverThumbnailUrl: n % 4 === 0 ? null : `${PHOTOS}/cover-${n}.png`,
    photoCount: n % 4 === 0 ? 0 : (n % 7) + 1,
    summary: Object.fromEntries(Object.entries(attributes).slice(0, 3)),
    publishedAt: new Date(BASE_TIME - n * 37 * 60_000).toISOString(),
    attributes,
    ...fields,
  };
}

function car(n: number, title: string, governorate: string, fuel: string, year: number) {
  return listing(n, {
    title,
    categoryCode: 'vehicles.cars',
    governorateCode: governorate,
    delegationCode: governorate === 'sfax' && n % 2 ? 'sfax.sakiet-ezzit' : null,
    attributes: {
      year,
      'mileage-km': 30000 + n * 1700,
      fuel,
      gearbox: 'manual',
      brand: title.split(' ')[0],
    },
  });
}

export function searchListings(): FakeSearchCard[] {
  const listings: FakeSearchCard[] = [];
  let n = 1;
  for (let i = 0; i < 31; i++) {
    listings.push(
      car(n++, `Peugeot 208 diesel, première main n°${i + 1}`, 'sfax', 'diesel', 2012 + (i % 12)),
    );
  }
  for (let i = 0; i < 17; i++) {
    listings.push(car(n++, `Peugeot 301 essence n°${i + 1}`, 'sfax', 'petrol', 2014 + (i % 9)));
  }
  for (let i = 0; i < 9; i++) {
    listings.push(car(n++, `Renault Clio n°${i + 1}`, 'tunis', 'petrol', 2015 + (i % 7)));
  }
  for (let i = 0; i < 3; i++) {
    listings.push(car(n++, `سيارة كيا بيكانتو للبيع ${i + 1}`, 'tunis', 'petrol', 2016 + i));
  }
  for (let i = 0; i < 5; i++) {
    listings.push(
      listing(n++, {
        title: `Appartement S+2 meublé n°${i + 1}`,
        categoryCode: 'real-estate.apartments',
        governorateCode: 'sousse',
        price: 900 + i * 100,
        attributes: { transaction: 'rent', 'surface-m2': 80 + i * 5, rooms: 2, furnished: true },
      }),
    );
  }
  for (let i = 0; i < 2; i++) {
    listings.push(
      listing(n++, {
        title: `Cours de maths, bac et 9e année n°${i + 1}`,
        kind: 'SERVICE',
        categoryCode: 'services.other',
        governorateCode: 'ariana',
        priceMode: 'ON_QUOTE',
        price: null,
      }),
    );
  }
  return listings;
}

const normalize = (text: string) =>
  text
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '');

function matches(card: FakeSearchCard, params: URLSearchParams): boolean {
  const q = params.get('q');
  if (q && !normalize(card.title).includes(normalize(q))) return false;
  const category = params.get('category');
  if (category && card.categoryCode !== category && !card.categoryCode.startsWith(`${category}.`)) {
    return false;
  }
  const governorate = params.get('governorate');
  if (governorate && card.governorateCode !== governorate) return false;
  const delegation = params.get('delegation');
  if (delegation && card.delegationCode !== delegation) return false;
  const priceMin = params.get('priceMin');
  const priceMax = params.get('priceMax');
  if ((priceMin || priceMax) && card.price === null) return false;
  if (priceMin && (card.price ?? 0) < Number(priceMin)) return false;
  if (priceMax && (card.price ?? 0) > Number(priceMax)) return false;
  for (const [name, value] of params) {
    if (!name.startsWith('attr.')) continue;
    const [, code, bound] = name.split('.');
    const actual = card.attributes[code];
    if (bound === 'min' && !(Number(actual) >= Number(value))) return false;
    if (bound === 'max' && !(Number(actual) <= Number(value))) return false;
    if (!bound && !value.split(',').includes(String(actual))) return false;
  }
  return true;
}

export async function fakeSearchApi(page: Page): Promise<FakeSearchApi> {
  const api: FakeSearchApi = { listings: searchListings(), requests: [], offline: false };
  await page.route(
    (url) => url.pathname === '/api/listings',
    async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      if (request.method() !== 'GET') {
        return route.fallback();
      }
      api.requests.push(url.searchParams);
      if (api.offline) {
        return route.abort('internetdisconnected');
      }
      const params = url.searchParams;
      if (params.get('category') === 'unknown') {
        return route.fulfill({
          status: 400,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            ...problem('invalid-search', 400),
            code: 'filter.unknown-category',
          }),
        });
      }
      let found = api.listings.filter((card) => matches(card, params));
      const sort = params.get('sort');
      if (sort === 'price-asc' || sort === 'price-desc') {
        const sign = sort === 'price-asc' ? 1 : -1;
        found = [...found].sort((a, b) => sign * ((a.price ?? 0) - (b.price ?? 0)));
      }
      const offset = Number(params.get('cursor') ?? 0);
      const limit = Number(params.get('limit') ?? 24);
      const items = found
        .slice(offset, offset + limit)
        .map(({ attributes: _attributes, ...card }) => card);
      const body: Record<string, unknown> = { items };
      if (offset + limit < found.length) body['nextCursor'] = String(offset + limit);
      if (!params.has('cursor')) body['total'] = found.length;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
      });
    },
  );
  return api;
}
