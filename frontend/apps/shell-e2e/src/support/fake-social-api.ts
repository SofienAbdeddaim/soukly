import { Page, Route } from '@playwright/test';

/**
 * Fakes social-publisher (SOC-01) in the browser, on top of `fakeAccountApi` (register it after: the
 * last route registered wins). Facebook itself is never opened: the `authorizeUrl` of "Connect"
 * leads straight back to `/oauth/facebook/callback`, as Facebook would after the member's answer.
 */
export type FacebookScenario =
  'one-page' | 'several-pages' | 'cancelled' | 'permission-missing' | 'no-page';

export interface FakeSocialApi {
  scenario: FacebookScenario;
  /** Facebook connection of the member, null when not connected. */
  facebook: FakeConnection | null;
  /** Language sent by the last "Connect", given back by the callback. */
  lang: string;
  /** Pages of the choice in progress (`several-pages`). */
  pending: FakePage[] | null;
  calls: { method: string; path: string; body: unknown }[];
}

export interface FakeConnection {
  network: 'FACEBOOK';
  status: 'CONNECTED' | 'NEEDS_RECONNECT';
  displayName: string;
  pictureUrl: string | null;
  connectedAt: string;
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
  };
}

const PROBLEMS: Partial<Record<FacebookScenario, string>> = {
  cancelled: 'oauth-cancelled',
  'permission-missing': 'oauth-permission-missing',
  'no-page': 'facebook-no-page',
};

export async function fakeSocialApi(
  page: Page,
  options: Partial<Pick<FakeSocialApi, 'scenario' | 'facebook'>> = {},
): Promise<FakeSocialApi> {
  const api: FakeSocialApi = {
    scenario: 'one-page',
    facebook: null,
    lang: 'fr',
    pending: null,
    calls: [],
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
      return json(route, 200, api.facebook ? [api.facebook] : []);
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
    return json(route, 503, problem('unavailable', 503));
  });
  return api;
}
