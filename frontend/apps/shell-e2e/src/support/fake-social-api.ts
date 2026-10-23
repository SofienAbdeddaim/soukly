import { Page, Route } from '@playwright/test';

/**
 * Fakes social-publisher (SOC-01, SOC-02, SOC-03) in the browser, on top of `fakeAccountApi` (register it
 * after: the last route registered wins). Facebook and Instagram themselves are never opened: the
 * `authorizeUrl` of "Connect" leads straight back to `/oauth/facebook/callback` or
 * `/oauth/instagram/callback`, as the network would after the member's answer.
 */
export type FacebookScenario =
  'one-page' | 'several-pages' | 'cancelled' | 'permission-missing' | 'no-page';

export type InstagramScenario = 'business' | 'personal' | 'cancelled';

export interface FakeSocialApi {
  scenario: FacebookScenario;
  instagramScenario: InstagramScenario;
  /** Facebook connection of the member, null when not connected. */
  facebook: FakeConnection | null;
  /** Instagram connection of the member, null when not connected. */
  instagram: FakeConnection | null;
  /** Language sent by the last "Connect", given back by the callback. */
  lang: string;
  /** Pages of the choice in progress (`several-pages`). */
  pending: FakePage[] | null;
  calls: { method: string; path: string; body: unknown }[];
  /** SOC-03: "See the post text" answers 503 while true. */
  captionDown: boolean;
}

export interface FakeConnection {
  network: 'FACEBOOK' | 'INSTAGRAM';
  status: 'CONNECTED' | 'NEEDS_RECONNECT';
  displayName: string;
  pictureUrl: string | null;
  connectedAt: string;
  autoShare: boolean;
}

export interface FakePage {
  pageId: string;
  name: string;
  category: string | null;
  pictureUrl: string | null;
}

export const CONNECTED_AT = '2026-10-12T09:30:00Z';

export const PAGES: FakePage[] = [
  { pageId: '1001', name: 'Sfax Auto Occasion', category: 'Concessionnaire', pictureUrl: null },
  { pageId: '1002', name: 'Atelier Jasmin', category: 'Artisanat', pictureUrl: null },
  { pageId: '1003', name: 'Djerba Location', category: 'Immobilier', pictureUrl: null },
];

export function connection(name = 'Sfax Auto Occasion'): FakeConnection {
  return {
    network: 'FACEBOOK',
    status: 'CONNECTED',
    displayName: name,
    pictureUrl: null,
    connectedAt: CONNECTED_AT,
    autoShare: true,
  };
}

export function instagramConnection(username = '@sfax.auto'): FakeConnection {
  return {
    network: 'INSTAGRAM',
    status: 'CONNECTED',
    displayName: username,
    pictureUrl: null,
    connectedAt: CONNECTED_AT,
    autoShare: true,
  };
}

interface CaptionBody {
  title: string;
  description: string;
  priceTnd: number | null;
  negotiable: boolean;
  publicUrl: string;
  language: string;
  network?: string;
  shortRef?: string | null;
}

/**
 * A post text of the shape of `CaptionBuilder` for the cars of Sfax of the tests: the real
 * template lives in social-publisher only (its own tests), never in the front end.
 */
function caption(body: CaptionBody) {
  const ar = body.language === 'ar';
  const price = ar ? '32.000 د.ت.' : '32 000 DT';
  const negotiable = ar ? 'قابل للتفاوض' : 'Négociable';
  const hashtags = ar ? ['#Soukly', '#صفاقس', '#سيارات'] : ['#Soukly', '#Sfax', '#Voitures'];
  const text = [
    body.title,
    body.negotiable ? `${price} · ${negotiable}` : price,
    ar ? 'صفاقس، ساقية الزيت' : 'Sfax, Sakiet Ezzit',
    '',
    body.description,
    '',
    body.network === 'INSTAGRAM'
      ? `${ar ? 'الإعلان كاملًا على' : 'Annonce complète sur'} ${new URL(body.publicUrl).host}/r/${body.shortRef}`
      : `${ar ? 'شاهد الإعلان:' : "Voir l'annonce :"} ${body.publicUrl}`,
    hashtags.join(' '),
  ].join('\n');
  return { text, hashtags };
}

const PROBLEMS: Partial<Record<FacebookScenario, string>> = {
  cancelled: 'oauth-cancelled',
  'permission-missing': 'oauth-permission-missing',
  'no-page': 'facebook-no-page',
};

export async function fakeSocialApi(
  page: Page,
  options: Partial<
    Pick<FakeSocialApi, 'scenario' | 'instagramScenario' | 'facebook' | 'instagram' | 'captionDown'>
  > = {},
): Promise<FakeSocialApi> {
  const api: FakeSocialApi = {
    scenario: 'one-page',
    instagramScenario: 'business',
    facebook: null,
    instagram: null,
    lang: 'fr',
    pending: null,
    calls: [],
    captionDown: false,
    ...options,
  };
  const json = (route: Route, status: number, body?: unknown) =>
    route.fulfill({
      status,
      contentType: status >= 400 ? 'application/problem+json' : 'application/json',
      body: body === undefined ? '' : JSON.stringify(body),
    });
  const problem = (slug: string, status: number, lang?: string) => ({
    type: `urn:soukly:problem:${slug}`,
    title: slug,
    status,
    ...(lang ? { lang } : {}),
  });

  await page.route('**/api/social/**', async (route) => {
    const request = route.request();
    const method = request.method();
    const path = new URL(request.url()).pathname;
    const body = request.postData() ? request.postDataJSON() : null;
    api.calls.push({ method, path, body });

    if (method === 'GET' && path === '/api/social/connections') {
      return json(
        route,
        200,
        [api.facebook, api.instagram].filter((connection) => connection !== null),
      );
    }
    const preference = /^\/api\/social\/connections\/(facebook|instagram)$/.exec(path);
    if (method === 'PATCH' && preference) {
      const current = preference[1] === 'facebook' ? api.facebook : api.instagram;
      if (!current) {
        return json(route, 404, problem('connection-not-found', 404));
      }
      current.autoShare = (body as { autoShare: boolean }).autoShare;
      return json(route, 200, current);
    }
    if (method === 'POST' && path === '/api/social/caption-preview') {
      if (api.captionDown) {
        return json(route, 503, problem('caption-unavailable', 503));
      }
      return json(route, 200, caption(body as CaptionBody));
    }
    if (method === 'POST' && path === '/api/social/facebook/connect') {
      api.lang = (body as { lang: string }).lang;
      const answer =
        api.scenario === 'cancelled'
          ? 'error=access_denied&state=fake-state'
          : 'code=fake-code&state=fake-state';
      return json(route, 200, { authorizeUrl: `/oauth/facebook/callback?${answer}` });
    }
    if (method === 'POST' && path === '/api/social/facebook/callback') {
      const failure = PROBLEMS[api.scenario];
      if (failure) {
        return json(route, 400, problem(failure, 400, api.lang));
      }
      if (api.scenario === 'several-pages') {
        api.pending = PAGES;
        return json(route, 200, { lang: api.lang, pendingSelection: PAGES });
      }
      api.facebook = connection();
      return json(route, 200, { lang: api.lang, connection: api.facebook });
    }
    if (method === 'GET' && path === '/api/social/facebook/page-selection') {
      return api.pending
        ? json(route, 200, { pendingSelection: api.pending })
        : json(route, 404, problem('page-selection-not-found', 404));
    }
    if (method === 'POST' && path === '/api/social/facebook/page') {
      const chosen = api.pending?.find(
        (candidate) => candidate.pageId === (body as { pageId: string }).pageId,
      );
      if (!chosen) {
        return json(route, 404, problem('page-selection-not-found', 404));
      }
      api.pending = null;
      api.facebook = connection(chosen.name);
      return json(route, 200, api.facebook);
    }
    if (method === 'DELETE' && path === '/api/social/facebook') {
      api.facebook = null;
      return route.fulfill({ status: 204 });
    }
    if (method === 'POST' && path === '/api/social/instagram/connect') {
      api.lang = (body as { lang: string }).lang;
      const answer =
        api.instagramScenario === 'cancelled'
          ? 'error=access_denied&state=fake-state'
          : 'code=fake-code&state=fake-state';
      return json(route, 200, { authorizeUrl: `/oauth/instagram/callback?${answer}` });
    }
    if (method === 'POST' && path === '/api/social/instagram/callback') {
      if (api.instagramScenario === 'cancelled') {
        return json(route, 400, problem('oauth-cancelled', 400, api.lang));
      }
      if (api.instagramScenario === 'personal') {
        return json(route, 400, problem('instagram-not-professional', 400, api.lang));
      }
      api.instagram = instagramConnection();
      return json(route, 200, { lang: api.lang, connection: api.instagram });
    }
    if (method === 'DELETE' && path === '/api/social/instagram') {
      api.instagram = null;
      return route.fulfill({ status: 204 });
    }
    return json(route, 503, problem('unavailable', 503));
  });
  return api;
}
