import { Params } from '@angular/router';
import { Lang } from '@soukly/i18n';
import { SEARCH_SORTS, SearchParams, SearchSort } from '@soukly/listing-data';

/**
 * A search of the catalogue. The URL is its only source of truth (CATA-02): the page reads it from
 * the route and changes it by navigating, with the parameter names of the API (CATA-01). The
 * category lives in the path (`/{lang}/c/vehicles.cars`), every other criterion in the query.
 */
export interface SearchCriteria {
  q: string | null;
  /** A family (`vehicles`) or a subcategory (`vehicles.cars`). */
  category: string | null;
  governorate: string | null;
  /** Only with its governorate. */
  delegation: string | null;
  priceMin: number | null;
  priceMax: number | null;
  /**
   * `attr.*` parameters without their prefix, only with a subcategory: `fuel` -> `diesel,petrol`,
   * `year.min` -> `2015`, `furnished` -> `true`, `brand` -> `Peugeot`.
   */
  attributes: Record<string, string>;
  /** Null: the default sort, `relevance` with a text query, `recent` otherwise. */
  sort: SearchSort | null;
}

export const EMPTY_CRITERIA: SearchCriteria = {
  q: null,
  category: null,
  governorate: null,
  delegation: null,
  priceMin: null,
  priceMax: null,
  attributes: {},
  sort: null,
};

export const ATTRIBUTE_PARAM_PREFIX = 'attr.';

/** Most characters of a text query (CATA-01). */
export const MAX_QUERY_LENGTH = 100;

/** A subcategory code has a family prefix: `vehicles.cars`. */
export function isSubcategory(code: string | null): code is string {
  return !!code && code.includes('.');
}

function text(value: unknown): string | null {
  const string = typeof value === 'string' ? value.trim() : '';
  return string ? string : null;
}

/** A whole number written with Latin digits; anything else is ignored. */
function wholeNumber(value: unknown): number | null {
  const string = text(value);
  return string && /^\d{1,12}$/u.test(string) ? Number(string) : null;
}

/**
 * Criteria of a URL: the category of the path (or of `category` in the query), then the query
 * parameters. Unknown or malformed parameters are dropped rather than sent to the API, and so are
 * the combinations it refuses (a delegation without governorate, `attr.*` without subcategory).
 */
export function criteriaFromUrl(pathCategory: string | null, query: Params): SearchCriteria {
  const category = text(pathCategory) ?? text(query['category']);
  const governorate = text(query['governorate']);
  const sort = text(query['sort']);
  const attributes: Record<string, string> = {};
  if (isSubcategory(category)) {
    for (const name of Object.keys(query).sort()) {
      const value = text(query[name]);
      if (name.startsWith(ATTRIBUTE_PARAM_PREFIX) && name.length > 5 && value) {
        attributes[name.slice(ATTRIBUTE_PARAM_PREFIX.length)] = value;
      }
    }
  }
  return {
    q: text(query['q'])?.slice(0, MAX_QUERY_LENGTH) ?? null,
    category,
    governorate,
    delegation: governorate ? text(query['delegation']) : null,
    priceMin: wholeNumber(query['priceMin']),
    priceMax: wholeNumber(query['priceMax']),
    attributes,
    sort: SEARCH_SORTS.includes(sort as SearchSort) ? (sort as SearchSort) : null,
  };
}

/** Query parameters of the criteria, without the category, in a stable order. */
export function criteriaQueryParams(criteria: SearchCriteria): Params {
  const params: Params = {};
  const set = (name: string, value: string | number | null) => {
    if (value !== null && value !== '') {
      params[name] = String(value);
    }
  };
  set('q', criteria.q);
  set('governorate', criteria.governorate);
  set('delegation', criteria.delegation);
  set('priceMin', criteria.priceMin);
  set('priceMax', criteria.priceMax);
  for (const name of Object.keys(criteria.attributes).sort()) {
    set(ATTRIBUTE_PARAM_PREFIX + name, criteria.attributes[name]);
  }
  set('sort', criteria.sort);
  return params;
}

/** Router commands of the page of the criteria: `/{lang}/c/{category}` or `/{lang}/search`. */
export function criteriaCommands(lang: Lang, criteria: SearchCriteria): string[] {
  return criteria.category ? ['/', lang, 'c', criteria.category] : ['/', lang, 'search'];
}

/** Sort actually applied by the API. */
export function effectiveSort(criteria: SearchCriteria): SearchSort {
  if (criteria.sort && (criteria.sort !== 'relevance' || criteria.q)) {
    return criteria.sort;
  }
  return criteria.q ? 'relevance' : 'recent';
}

/** Parameters of `GET /listings` for the criteria; the sort is sent only when chosen. */
export function apiParams(criteria: SearchCriteria, extra: SearchParams = {}): SearchParams {
  const params: SearchParams = { ...criteriaQueryParams(criteria) };
  if (criteria.category) {
    params['category'] = criteria.category;
  }
  if (criteria.sort === 'relevance' && !criteria.q) {
    delete params['sort'];
  }
  // Both bounds in the right order: the API refuses a minimum above the maximum.
  if (criteria.priceMin !== null && criteria.priceMax !== null) {
    params['priceMin'] = Math.min(criteria.priceMin, criteria.priceMax);
    params['priceMax'] = Math.max(criteria.priceMin, criteria.priceMax);
  }
  return { ...params, ...extra };
}

/** Same search, whatever the order of the attributes. */
export function sameCriteria(a: SearchCriteria, b: SearchCriteria): boolean {
  return (
    JSON.stringify(criteriaQueryParams(a)) === JSON.stringify(criteriaQueryParams(b)) &&
    a.category === b.category
  );
}

/** Another text query: a `relevance` sort without text goes back to the default. */
export function withQuery(criteria: SearchCriteria, q: string | null): SearchCriteria {
  const query = text(q)?.slice(0, MAX_QUERY_LENGTH) ?? null;
  const sort = criteria.sort === 'relevance' && !query ? null : criteria.sort;
  return { ...criteria, q: query, sort };
}

/** Another category: the specific filters of the previous subcategory are dropped. */
export function withCategory(
  criteria: SearchCriteria,
  category: string | null,
  keptAttributes: readonly string[] = [],
): SearchCriteria {
  if (category === criteria.category) {
    return criteria;
  }
  const attributes: Record<string, string> = {};
  if (isSubcategory(category)) {
    for (const [name, value] of Object.entries(criteria.attributes)) {
      if (keptAttributes.includes(attributeCode(name))) {
        attributes[name] = value;
      }
    }
  }
  return { ...criteria, category, attributes };
}

/** Another governorate: its delegation goes with it. */
export function withGovernorate(
  criteria: SearchCriteria,
  governorate: string | null,
): SearchCriteria {
  return governorate === criteria.governorate
    ? criteria
    : { ...criteria, governorate, delegation: null };
}

/** Code of an attribute parameter: `year.min` -> `year`. */
export function attributeCode(name: string): string {
  return name.replace(/\.(min|max)$/u, '');
}

/** One active filter, shown as a chip and counted on the "Filters" button. */
export type ActiveFilter =
  | { kind: 'category'; code: string }
  | { kind: 'governorate'; code: string }
  | { kind: 'delegation'; code: string }
  | { kind: 'price'; min: number | null; max: number | null }
  | { kind: 'enum'; code: string; value: string }
  | { kind: 'range'; code: string; min: string | null; max: string | null }
  | { kind: 'flag'; code: string; value: string }
  | { kind: 'text'; code: string; value: string };

/**
 * The active filters, in the order of the filter panel. An enumeration counts one filter per
 * chosen value, so each one can be removed alone; `kind` of an attribute is read from its
 * parameters (`.min`/`.max`: a range, `true`/`false`: a flag, a comma list: an enumeration),
 * then refined by the page with its definition.
 */
export function activeFilters(
  criteria: SearchCriteria,
  attributeTypes: Record<string, 'ENUM' | 'INTEGER' | 'BOOLEAN' | 'TEXT'> = {},
): ActiveFilter[] {
  const filters: ActiveFilter[] = [];
  if (criteria.category) {
    filters.push({ kind: 'category', code: criteria.category });
  }
  if (criteria.governorate) {
    filters.push({ kind: 'governorate', code: criteria.governorate });
  }
  if (criteria.delegation) {
    filters.push({ kind: 'delegation', code: criteria.delegation });
  }
  if (criteria.priceMin !== null || criteria.priceMax !== null) {
    filters.push({ kind: 'price', min: criteria.priceMin, max: criteria.priceMax });
  }
  const seen = new Set<string>();
  for (const name of Object.keys(criteria.attributes)) {
    const code = attributeCode(name);
    if (seen.has(code)) {
      continue;
    }
    seen.add(code);
    const value = criteria.attributes[code];
    const type = attributeTypes[code];
    if (type === 'INTEGER' || name !== code) {
      filters.push({
        kind: 'range',
        code,
        min: criteria.attributes[`${code}.min`] ?? null,
        max: criteria.attributes[`${code}.max`] ?? null,
      });
    } else if (type === 'BOOLEAN' || (!type && (value === 'true' || value === 'false'))) {
      filters.push({ kind: 'flag', code, value });
    } else if (type === 'TEXT') {
      filters.push({ kind: 'text', code, value });
    } else {
      for (const item of value
        .split(',')
        .map((part) => part.trim())
        .filter(Boolean)) {
        filters.push({ kind: 'enum', code, value: item });
      }
    }
  }
  return filters;
}

/** The criteria without one of their filters. */
export function withoutFilter(criteria: SearchCriteria, filter: ActiveFilter): SearchCriteria {
  const attributes = { ...criteria.attributes };
  switch (filter.kind) {
    case 'category':
      return withCategory(criteria, null);
    case 'governorate':
      return withGovernorate(criteria, null);
    case 'delegation':
      return { ...criteria, delegation: null };
    case 'price':
      return { ...criteria, priceMin: null, priceMax: null };
    case 'range':
      delete attributes[`${filter.code}.min`];
      delete attributes[`${filter.code}.max`];
      return { ...criteria, attributes };
    case 'enum': {
      const values = (attributes[filter.code] ?? '')
        .split(',')
        .map((part) => part.trim())
        .filter((value) => value && value !== filter.value);
      if (values.length) {
        attributes[filter.code] = values.join(',');
      } else {
        delete attributes[filter.code];
      }
      return { ...criteria, attributes };
    }
    case 'flag':
    case 'text':
      delete attributes[filter.code];
      return { ...criteria, attributes };
  }
}

/** The criteria without any filter: only the text query and the sort stay. */
export function withoutFilters(criteria: SearchCriteria): SearchCriteria {
  return { ...EMPTY_CRITERIA, q: criteria.q, sort: criteria.sort };
}
