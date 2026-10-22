import { Page, Route } from '@playwright/test';
import { problem } from './fake-api';

/**
 * Fakes listing-service in the browser (the CI has no back end): referentials (a part of CAT-01),
 * drafts with their versions (ANN-01), photos with presigned uploads (ANN-02), publication
 * (ANN-03), "My listings" with its actions (ANN-05). The storage of the uploads is `http://storage.test`, the photos are served from `http://photos.test`.
 * Register it after `fakeAccountApi`: what it does not answer falls back to the account API.
 */
const STORAGE = 'http://storage.test';
export const PHOTOS = 'http://photos.test';

type Labels = { fr: string; ar: string; en: string };
const l = (fr: string, ar: string, en: string): Labels => ({ fr, ar, en });

export const FAMILIES = [
  {
    code: 'vehicles',
    label: l('Véhicules', 'المركبات', 'Vehicles'),
    subcategories: [
      { code: 'vehicles.cars', familyCode: 'vehicles', label: l('Voitures', 'سيارات', 'Cars') },
      {
        code: 'vehicles.motorcycles',
        familyCode: 'vehicles',
        label: l('Motos et scooters', 'دراجات نارية وسكوترات', 'Motorcycles and scooters'),
      },
    ],
  },
  {
    code: 'real-estate',
    label: l('Immobilier', 'العقارات', 'Real estate'),
    subcategories: [
      {
        code: 'real-estate.apartments',
        familyCode: 'real-estate',
        label: l('Appartements', 'شقق', 'Apartments'),
      },
    ],
  },
  ...(
    [
      ['jobs', l('Emploi', 'الشغل', 'Jobs')],
      ['services', l('Services', 'الخدمات', 'Services')],
      ['home', l('Maison', 'المنزل', 'Home')],
      ['multimedia', l('Multimédia', 'الإلكترونيات', 'Multimedia')],
      ['fashion', l('Mode', 'الموضة', 'Fashion')],
      ['leisure', l('Loisirs', 'الترفيه', 'Leisure')],
      ['animals', l('Animaux', 'الحيوانات', 'Animals')],
      ['misc', l('Divers', 'متفرقات', 'Miscellaneous')],
    ] as const
  ).map(([code, label]) => ({
    code,
    label,
    subcategories: [
      { code: `${code}.other`, familyCode: code, label: l('Autres', 'أخرى', 'Other') },
    ],
  })),
];

export const GOVERNORATES = [
  { code: 'tunis', label: l('Tunis', 'تونس', 'Tunis') },
  { code: 'ariana', label: l('Ariana', 'أريانة', 'Ariana') },
  { code: 'sfax', label: l('Sfax', 'صفاقس', 'Sfax') },
  { code: 'sousse', label: l('Sousse', 'سوسة', 'Sousse') },
];

const DELEGATIONS: Record<string, { code: string; label: Labels }[]> = {
  sfax: [
    { code: 'sfax.sfax-ville', label: l('Sfax Ville', 'صفاقس المدينة', 'Sfax Ville') },
    { code: 'sfax.sakiet-ezzit', label: l('Sakiet Ezzit', 'ساقية الزيت', 'Sakiet Ezzit') },
  ],
};

const enumValues = (...values: [string, Labels][]) =>
  values.map(([code, label]) => ({ code, label }));

export const ATTRIBUTES: Record<string, Record<string, unknown>[]> = {
  'vehicles.cars': [
    {
      code: 'brand',
      type: 'TEXT',
      required: true,
      minLength: 2,
      maxLength: 40,
      filterable: true,
      label: l('Marque', 'الماركة', 'Brand'),
    },
    {
      code: 'model',
      type: 'TEXT',
      required: false,
      minLength: 1,
      maxLength: 40,
      filterable: false,
      label: l('Modèle', 'الطراز', 'Model'),
    },
    {
      code: 'year',
      type: 'INTEGER',
      required: true,
      minValue: 1950,
      maxValue: 2027,
      filterable: true,
      label: l('Année', 'سنة الصنع', 'Year'),
    },
    {
      code: 'mileage-km',
      type: 'INTEGER',
      required: true,
      minValue: 0,
      maxValue: 2000000,
      filterable: true,
      label: l('Kilométrage (km)', 'عدد الكيلومترات (كم)', 'Mileage (km)'),
    },
    {
      code: 'fuel',
      type: 'ENUM',
      required: true,
      filterable: true,
      label: l('Carburant', 'الوقود', 'Fuel'),
      values: enumValues(
        ['petrol', l('Essence', 'بنزين', 'Petrol')],
        ['diesel', l('Diesel', 'ديزل', 'Diesel')],
        ['hybrid', l('Hybride', 'هجين', 'Hybrid')],
        ['electric', l('Électrique', 'كهربائي', 'Electric')],
        ['lpg', l('GPL', 'غاز (GPL)', 'LPG')],
      ),
    },
    {
      code: 'gearbox',
      type: 'ENUM',
      required: true,
      filterable: true,
      label: l('Boîte de vitesses', 'علبة السرعات', 'Gearbox'),
      values: enumValues(
        ['manual', l('Manuelle', 'يدوية', 'Manual')],
        ['automatic', l('Automatique', 'أوتوماتيكية', 'Automatic')],
      ),
    },
  ],
  'real-estate.apartments': [
    {
      code: 'transaction',
      type: 'ENUM',
      required: true,
      filterable: true,
      label: l("Type d'offre", 'نوع العرض', 'Offer type'),
      values: enumValues(
        ['sale', l('Vente', 'بيع', 'Sale')],
        ['rent', l('Location', 'كراء', 'Rent')],
      ),
    },
    {
      code: 'surface-m2',
      type: 'INTEGER',
      required: true,
      minValue: 1,
      maxValue: 100000,
      filterable: true,
      label: l('Surface (m²)', 'المساحة (م²)', 'Area (m²)'),
    },
    {
      code: 'rooms',
      type: 'INTEGER',
      required: true,
      minValue: 0,
      maxValue: 20,
      filterable: true,
      label: l('Nombre de chambres', 'عدد الغرف', 'Bedrooms'),
    },
    {
      code: 'furnished',
      type: 'BOOLEAN',
      required: false,
      filterable: true,
      label: l('Meublé', 'مفروش', 'Furnished'),
    },
  ],
};

export interface FakePhoto {
  id: string;
  url: string;
  thumbnailUrl: string;
  width: number;
  height: number;
  position: number;
}

export interface FakeListing {
  id: string;
  version: number;
  categoryCode: string;
  title: string | null;
  description: string | null;
  priceMode: string | null;
  price: number | null;
  negotiable: boolean;
  governorateCode: string | null;
  delegationCode: string | null;
  attributes: Record<string, unknown>;
  photos: FakePhoto[];
  status?: FakeStatus;
  /** End of visibility; 60 days after the publication when absent. */
  expiresAt?: string;
  contactRevealsCount?: number;
  /** SOC-03: null (absent) until the seller chose. */
  shareTo?: string[] | null;
  shareLanguage?: string | null;
}

export type FakeStatus = 'DRAFT' | 'PUBLISHED' | 'EXPIRED' | 'INACTIVE' | 'DELETED';

const DAY_MS = 24 * 60 * 60 * 1000;

/** An instant `days` from now (negative: in the past), as the API writes it. */
export function inDays(days: number): string {
  return new Date(Date.now() + days * DAY_MS).toISOString();
}

export interface FakeListingApi {
  listings: Map<string, FakeListing>;
  /** Bodies of the PATCH requests received. */
  patches: Record<string, unknown>[];
  /** Files received by the storage, by photo id. */
  uploads: Map<string, Buffer>;
  /** Actions received: `deactivate {id}`, `renew {id}`, `delete {id}`. */
  actions: string[];
  /** Changes the draft "in another tab": its version goes up. */
  changeElsewhere(id: string, changes: Partial<FakeListing>): void;
}

export const LISTING_ID = '0b9d6c3e-6a43-4c5e-9f0e-2d7a1b8c4e51';

export function draft(changes: Partial<FakeListing> = {}): FakeListing {
  return {
    id: LISTING_ID,
    version: 0,
    categoryCode: 'vehicles.cars',
    title: null,
    description: null,
    priceMode: null,
    price: null,
    negotiable: false,
    governorateCode: null,
    delegationCode: null,
    attributes: {},
    photos: [],
    ...changes,
  };
}

export function fakePhoto(id: string, position: number): FakePhoto {
  return {
    id,
    url: `${PHOTOS}/${id}.png`,
    thumbnailUrl: `${PHOTOS}/${id}-400.png`,
    width: 1600,
    height: 1200,
    position,
  };
}

function missingFields(listing: FakeListing): string[] {
  const missing: string[] = [];
  if (!listing.photos.length) missing.push('photos');
  if (!listing.title) missing.push('title');
  if (!listing.description) missing.push('description');
  if (!listing.priceMode) missing.push('priceMode');
  else if (listing.priceMode === 'FIXED' && listing.price === null) missing.push('price');
  if (!listing.governorateCode) missing.push('governorateCode');
  for (const attribute of ATTRIBUTES[listing.categoryCode] ?? []) {
    if (attribute['required'] && !(String(attribute['code']) in listing.attributes)) {
      missing.push(`attributes.${attribute['code']}`);
    }
  }
  return missing;
}

function kindOf(listing: FakeListing): string {
  const family = listing.categoryCode.split('.')[0];
  return family === 'services' || family === 'jobs' ? 'SERVICE' : 'SALE';
}

function view(listing: FakeListing, warnings: string[] = []) {
  const published = (listing.status ?? 'DRAFT') !== 'DRAFT';
  const fields: Partial<FakeListing> = { ...listing };
  delete fields.contactRevealsCount;
  return {
    ...fields,
    status: listing.status ?? 'DRAFT',
    shareTo: listing.shareTo ?? null,
    shareLanguage: listing.shareLanguage ?? null,
    kind: kindOf(listing),
    missingFields: missingFields(listing),
    warnings,
    createdAt: '2026-10-21T08:00:00Z',
    updatedAt: '2026-10-21T08:00:00Z',
    publishedAt: published ? '2026-10-21T09:00:00Z' : null,
    expiresAt: published ? (listing.expiresAt ?? '2026-12-20T09:00:00Z') : null,
  };
}

/** Card of "My listings" (`GET /listings/mine`, ANN-05). */
function card(listing: FakeListing) {
  const { id, status, title, categoryCode, priceMode, price, photos } = view(listing);
  const missing = missingFields(listing);
  return {
    id,
    status,
    title,
    kind: kindOf(listing),
    categoryCode,
    priceMode,
    price,
    currency: 'TND',
    coverThumbnailUrl: photos[0]?.thumbnailUrl ?? null,
    updatedAt: '2026-10-21T08:00:00Z',
    publishedAt: status === 'DRAFT' ? null : '2026-10-21T09:00:00Z',
    expiresAt: view(listing).expiresAt,
    contactRevealsCount: listing.contactRevealsCount ?? 0,
    ...(status === 'DRAFT' ? { missingFields: missing, missingFieldsCount: missing.length } : {}),
  };
}

/** One pixel PNG, for the thumbnails. */
const PIXEL = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);

export async function fakeListingApi(
  page: Page,
  initial: FakeListing[] = [],
): Promise<FakeListingApi> {
  const api: FakeListingApi = {
    listings: new Map(initial.map((listing) => [listing.id, structuredClone(listing)])),
    patches: [],
    uploads: new Map(),
    actions: [],
    changeElsewhere: (id, changes) => {
      const listing = api.listings.get(id);
      if (listing) {
        Object.assign(listing, changes, { version: listing.version + 1 });
      }
    },
  };
  let nextPhoto = 1;
  const json = (
    route: Route,
    status: number,
    body: unknown,
    headers: Record<string, string> = {},
  ) =>
    route.fulfill({
      status,
      headers,
      contentType: status >= 400 ? 'application/problem+json' : 'application/json',
      body: JSON.stringify(body),
    });
  const answer = (route: Route, listing: FakeListing, status = 200, warnings: string[] = []) =>
    json(route, status, view(listing, warnings), { ETag: `"${listing.version}"` });

  await page.route(`${STORAGE}/**`, async (route) => {
    const cors = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'PUT',
      'Access-Control-Allow-Headers': '*',
    };
    if (route.request().method() === 'OPTIONS') {
      return route.fulfill({ status: 204, headers: cors });
    }
    const id = new URL(route.request().url()).pathname.slice(1);
    api.uploads.set(id, route.request().postDataBuffer() ?? Buffer.alloc(0));
    return route.fulfill({ status: 200, headers: cors });
  });
  await page.route(`${PHOTOS}/**`, (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL }),
  );

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();

    if (path === '/api/categories') {
      return json(route, 200, FAMILIES);
    }
    if (path === '/api/locations/governorates') {
      return json(route, 200, GOVERNORATES);
    }
    const delegations = path.match(/^\/api\/locations\/governorates\/([^/]+)\/delegations$/);
    if (delegations) {
      return json(route, 200, DELEGATIONS[decodeURIComponent(delegations[1])] ?? []);
    }
    const attributes = path.match(/^\/api\/categories\/([^/]+)\/attributes$/);
    if (attributes) {
      return json(route, 200, ATTRIBUTES[decodeURIComponent(attributes[1])] ?? []);
    }
    if (path === '/api/listings' && method === 'POST') {
      const body = request.postDataJSON() as { categoryCode: string };
      const listing = draft({ categoryCode: body.categoryCode });
      api.listings.set(listing.id, listing);
      return answer(route, listing, 201);
    }
    if (path === '/api/listings/mine' && method === 'GET') {
      const status = new URL(request.url()).searchParams.get('status') ?? 'PUBLISHED';
      const own = [...api.listings.values()];
      const statusOf = (listing: FakeListing) => listing.status ?? 'DRAFT';
      const counts = Object.fromEntries(
        ['PUBLISHED', 'DRAFT', 'EXPIRED', 'INACTIVE'].map((value) => [
          value,
          own.filter((listing) => statusOf(listing) === value).length,
        ]),
      );
      return json(route, 200, {
        items: own.filter((listing) => statusOf(listing) === status).map(card),
        counts,
      });
    }
    const match = path.match(/^\/api\/listings\/([^/]+)(\/.*)?$/);
    const listing = match ? api.listings.get(match[1]) : undefined;
    if (!match) {
      return route.fallback();
    }
    if (!listing || listing.status === 'DELETED') {
      return json(route, 404, problem('listing-not-found', 404));
    }
    const rest = match[2] ?? '';
    if (rest === '' && method === 'GET') {
      return answer(route, listing);
    }
    if (rest === '' && method === 'PATCH') {
      if (request.headers()['if-match'] !== `"${listing.version}"`) {
        return json(route, 412, problem('version-conflict', 412));
      }
      const patch = request.postDataJSON() as Record<string, unknown>;
      api.patches.push(patch);
      const wasPublished = (listing.status ?? 'DRAFT') !== 'DRAFT';
      if (
        wasPublished &&
        'categoryCode' in patch &&
        patch['categoryCode'] !== listing.categoryCode
      ) {
        return json(route, 409, problem('category-locked', 409));
      }
      if (wasPublished) {
        const missing = missingFields({ ...listing, ...patch } as FakeListing);
        if (missing.length) {
          return json(route, 422, {
            ...problem('listing-incomplete', 422),
            missingFields: missing,
          });
        }
      }
      const warnings: string[] = [];
      for (const [key, value] of Object.entries(patch)) {
        if (key === 'attributes') {
          for (const [code, attribute] of Object.entries(
            (value ?? {}) as Record<string, unknown>,
          )) {
            if (attribute === null) delete listing.attributes[code];
            else listing.attributes[code] = attribute;
          }
        } else if (key === 'categoryCode' && value !== listing.categoryCode) {
          listing.categoryCode = String(value);
          const codes = (ATTRIBUTES[listing.categoryCode] ?? []).map((a) => a['code']);
          const before = Object.keys(listing.attributes).length;
          listing.attributes = Object.fromEntries(
            Object.entries(listing.attributes).filter(([code]) => codes.includes(code)),
          );
          if (Object.keys(listing.attributes).length < before) warnings.push('attributes-removed');
        } else {
          (listing as unknown as Record<string, unknown>)[key] = value;
        }
      }
      listing.version++;
      return answer(route, listing, 200, warnings);
    }
    if (rest === '/publish' && method === 'POST') {
      const { missingFields } = view(listing);
      if (missingFields.length) {
        return json(route, 422, { ...problem('listing-incomplete', 422), missingFields });
      }
      listing.status = 'PUBLISHED';
      return answer(route, listing);
    }
    if (rest === '/deactivate' && method === 'POST') {
      if (listing.status !== 'PUBLISHED') {
        return json(route, 409, problem('transition-not-allowed', 409));
      }
      api.actions.push(`deactivate ${listing.id}`);
      listing.status = 'INACTIVE';
      listing.version++;
      return answer(route, listing);
    }
    if (rest === '/renew' && method === 'POST') {
      api.actions.push(`renew ${listing.id}`);
      listing.status = 'PUBLISHED';
      listing.expiresAt = inDays(60);
      listing.version++;
      return answer(route, listing);
    }
    if (rest === '' && method === 'DELETE') {
      api.actions.push(`delete ${listing.id}`);
      if ((listing.status ?? 'DRAFT') === 'DRAFT') {
        api.listings.delete(listing.id);
      } else {
        listing.status = 'DELETED';
      }
      return route.fulfill({ status: 204 });
    }
    if (rest === '/photos' && method === 'POST') {
      const photoId = `photo-${nextPhoto++}`;
      return json(route, 201, {
        photoId,
        uploadUrl: `${STORAGE}/${photoId}`,
        method: 'PUT',
        headers: {
          'Content-Type': (request.postDataJSON() as { contentType: string }).contentType,
        },
        expiresAt: '2999-01-01T00:00:00Z',
      });
    }
    const complete = rest.match(/^\/photos\/([^/]+)\/complete$/);
    if (complete && method === 'POST') {
      const photo = fakePhoto(complete[1], listing.photos.length);
      listing.photos.push(photo);
      return json(route, 200, { ...photo, status: 'READY' });
    }
    if (rest === '/photos/order' && method === 'PUT') {
      const { photoIds } = request.postDataJSON() as { photoIds: string[] };
      listing.photos = photoIds.map((id, position) => ({
        ...(listing.photos.find((photo) => photo.id === id) as FakePhoto),
        position,
      }));
      return json(
        route,
        200,
        listing.photos.map((photo) => ({ ...photo, status: 'READY' })),
      );
    }
    const remove = rest.match(/^\/photos\/([^/]+)$/);
    if (remove && method === 'DELETE') {
      if ((listing.status ?? 'DRAFT') !== 'DRAFT' && listing.photos.length === 1) {
        return json(route, 422, {
          ...problem('listing-incomplete', 422),
          missingFields: ['photos'],
        });
      }
      listing.photos = listing.photos
        .filter((photo) => photo.id !== remove[1])
        .map((photo, position) => ({ ...photo, position }));
      return route.fulfill({ status: 204 });
    }
    return route.fallback();
  });
  return api;
}
