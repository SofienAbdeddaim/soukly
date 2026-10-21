import { Provider } from '@angular/core';
import { i18nTesting, Lang } from '@soukly/i18n';
import {
  APARTMENT_ATTRIBUTES,
  CAR_ATTRIBUTES,
  ReferentialsApi,
  SearchCard,
  SearchPage,
  SearchParams,
  SearchApi,
  SFAX_DELEGATIONS,
  TEST_FAMILIES,
  TEST_GOVERNORATES,
} from '@soukly/listing-data';
import { Observable, Subject } from 'rxjs';
import ar from '../../../public/i18n/catalog/ar.json';
import en from '../../../public/i18n/catalog/en.json';
import fr from '../../../public/i18n/catalog/fr.json';

/** Translations of the `catalog` scope, as the remote loads them. */
export function catalogI18n(lang: Lang = 'fr') {
  return i18nTesting(
    { 'catalog/fr': fr, 'catalog/ar': ar, 'catalog/en': en, fr: {}, ar: {}, en: {} },
    lang,
  );
}

/** A search result; `n` makes its id and title unique. */
export function card(n: number, overrides: Partial<SearchCard> = {}): SearchCard {
  return {
    id: `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`,
    title: `Peugeot 208 n°${n}`,
    kind: 'SALE',
    categoryCode: 'vehicles.cars',
    priceMode: 'FIXED',
    price: 32000,
    currency: 'TND',
    negotiable: false,
    governorateCode: 'sfax',
    delegationCode: 'sfax.sakiet-ezzit',
    coverThumbnailUrl: null,
    photoCount: 4,
    summary: { year: 2019, 'mileage-km': 62000, fuel: 'diesel' },
    publishedAt: '2026-10-21T08:00:00Z',
    ...overrides,
  };
}

/** A search API whose answers the test sends, one request at a time. */
export class FakeSearchApi {
  readonly requests: { params: SearchParams; answer: Subject<SearchPage> }[] = [];

  search(params: SearchParams): Observable<SearchPage> {
    const answer = new Subject<SearchPage>();
    this.requests.push({ params, answer });
    return answer;
  }

  last() {
    return this.requests[this.requests.length - 1];
  }

  /** Answers the last request. */
  respond(page: SearchPage): void {
    const { answer } = this.last();
    answer.next(page);
    answer.complete();
  }
}

/** Referentials of `@soukly/listing-data/testing`, answered at once. */
export const fakeReferentials: Partial<ReferentialsApi> = {
  categories: async () => TEST_FAMILIES,
  governorates: async () => TEST_GOVERNORATES,
  delegations: async (code: string) => (code === 'sfax' ? SFAX_DELEGATIONS : []),
  attributes: async (code: string) =>
    code === 'vehicles.cars'
      ? CAR_ATTRIBUTES
      : code === 'real-estate.apartments'
        ? APARTMENT_ATTRIBUTES
        : [],
};

export function catalogProviders(api: FakeSearchApi): Provider[] {
  return [
    { provide: SearchApi, useValue: api },
    { provide: ReferentialsApi, useValue: fakeReferentials },
  ];
}
