import { Page } from '@playwright/test';
import { problem } from './fake-api';
import { PHOTOS } from './fake-listing-api';

/**
 * Fakes the listing page of listing-service (CATA-03): `GET /api/listings/{id}` (the public view,
 * the owner's, `410` or `404`) and `POST /api/listings/{id}/contact`. Register it after
 * `fakeListingApi` and `fakeSearchApi`; other calls fall through to them.
 */
export const PUBLIC_ID = '11111111-1111-4111-8111-111111111111';
export const OWN_ID = '22222222-2222-4222-8222-222222222222';
export const GONE_ID = '33333333-3333-4333-8333-333333333333';
export const SELLER_PHONE = '+21620123456';

export interface FakeDetailApi {
  /** Contact requests received, with their `Authorization` header. */
  contacts: { id: string; authorization: string | undefined }[];
  /** Answers the next contacts with `429 contact-limit-reached`. */
  limitReached: boolean;
}

function photos(count: number) {
  return Array.from({ length: count }, (_, position) => ({
    id: `detail-photo-${position}`,
    url: `${PHOTOS}/detail-${position}-1600.png`,
    thumbnailUrl: `${PHOTOS}/detail-${position}-400.png`,
    width: 1600,
    height: 1200,
    position,
  }));
}

const BASE = {
  kind: 'SALE',
  categoryCode: 'vehicles.cars',
  title: 'Peugeot 208 diesel, première main',
  description:
    'Première main, carnet d’entretien complet.\nPneus neufs, contrôle technique valable.\n<b>Pas de</b> <script>échange</script>.',
  priceMode: 'FIXED',
  price: 32000,
  currency: 'TND',
  negotiable: true,
  governorateCode: 'sfax',
  delegationCode: 'sfax.sakiet-ezzit',
  attributes: {
    brand: 'Peugeot',
    year: 2019,
    'mileage-km': 62000,
    fuel: 'diesel',
    gearbox: 'manual',
  },
  photos: photos(4),
  publishedAt: new Date(Date.now() - 2 * 3_600_000).toISOString(),
};

export function publicDetail() {
  return {
    ...BASE,
    id: PUBLIC_ID,
    status: 'PUBLISHED',
    seller: {
      id: 'a1b2c3d4-0000-4000-8000-000000000001',
      displayName: 'Mohamed B.',
      memberSince: '2026-03',
    },
  };
}

/** A flat to rent in Arabic, for the RTL price "1.100 DT / month". */
export function arabicDetail() {
  return {
    ...publicDetail(),
    categoryCode: 'real-estate.apartments',
    title: 'شقة S+2 مفروشة للكراء',
    description: 'شقة مفروشة قريبة من البحر.',
    price: 1100,
    negotiable: false,
    governorateCode: 'sousse',
    delegationCode: null,
    attributes: { transaction: 'rent', 'surface-m2': 85, rooms: 2, furnished: true },
  };
}

function ownDetail() {
  return {
    ...BASE,
    id: OWN_ID,
    status: 'EXPIRED',
    version: 4,
    missingFields: [],
    warnings: [],
    createdAt: '2026-09-01T08:00:00Z',
    updatedAt: '2026-09-01T08:00:00Z',
    expiresAt: '2026-10-01T08:00:00Z',
    seller: {
      id: '6b7f1f9e-2f1c-4a8e-9a57-3c2d8f0e1a11',
      displayName: 'Amira B.',
      memberSince: '2026-01',
    },
  };
}

export async function fakeDetailApi(
  page: Page,
  listings: Record<string, unknown> = { [PUBLIC_ID]: publicDetail() },
): Promise<FakeDetailApi> {
  const api: FakeDetailApi = { contacts: [], limitReached: false };
  await page.route(
    (url) => /^\/api\/listings\/[^/]+(\/contact)?$/u.test(url.pathname),
    async (route) => {
      const request = route.request();
      const [, , , id, rest] = new URL(request.url()).pathname.split('/');
      const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
        route.fulfill({
          status,
          headers,
          contentType: status >= 400 ? 'application/problem+json' : 'application/json',
          body: JSON.stringify(body),
        });
      if (id === GONE_ID) {
        return json(410, {
          ...problem('listing-gone', 410),
          categoryCode: 'vehicles.cars',
          governorateCode: 'sfax',
        });
      }
      const signedIn = !!request.headers()['authorization'];
      if (id === OWN_ID) {
        return signedIn ? json(200, ownDetail()) : json(410, problem('listing-gone', 410));
      }
      const listing = listings[id];
      if (!listing) {
        return json(404, problem('listing-not-found', 404));
      }
      if (rest === 'contact' && request.method() === 'POST') {
        api.contacts.push({ id, authorization: request.headers()['authorization'] });
        if (!signedIn) {
          return json(401, problem('unauthorized', 401));
        }
        if (api.limitReached) {
          return json(
            429,
            { ...problem('contact-limit-reached', 429), code: 'contact-limit-reached' },
            { 'Retry-After': '3600' },
          );
        }
        return json(200, { phone: SELLER_PHONE }, { 'Cache-Control': 'no-store' });
      }
      if (!rest && request.method() === 'GET') {
        return json(200, listing);
      }
      return route.fallback();
    },
  );
  return api;
}
