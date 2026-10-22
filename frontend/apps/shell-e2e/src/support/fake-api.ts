import { Page, Route } from '@playwright/test';

/**
 * Fakes the account API of user-service in the browser (the CI has no back end). Unhandled `/api`
 * calls get a 503, so a test never depends on a server that may or may not run.
 */
export interface FakeAccountApi {
  /** Whether the refresh cookie "exists": `refresh` answers 200 with Amira's session, or 401. */
  signedIn: boolean;
  /** Answer of the next logins, by password. */
  logins: Record<string, { status: number; body?: unknown; headers?: Record<string, string> }>;
  /** Bodies of the calls received, by path. */
  calls: { path: string; body: unknown }[];
}

export function session(token = 'access-token', firstLogin = false) {
  return {
    accessToken: token,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: '6b7f1f9e-2f1c-4a8e-9a57-3c2d8f0e1a11',
      displayName: 'Amira B.',
      preferredLanguage: 'fr',
    },
    firstLogin,
  };
}

export function problem(slug: string, status: number) {
  return { type: `urn:soukly:problem:${slug}`, title: slug, status };
}

export async function fakeAccountApi(
  page: Page,
  options: Partial<Pick<FakeAccountApi, 'signedIn' | 'logins'>> = {},
): Promise<FakeAccountApi> {
  const api: FakeAccountApi = { signedIn: false, logins: {}, calls: [], ...options };
  const json = (route: Route, status: number, body?: unknown, headers?: Record<string, string>) =>
    route.fulfill({
      status,
      headers,
      contentType: status >= 400 ? 'application/problem+json' : 'application/json',
      body: body === undefined ? '' : JSON.stringify(body),
    });

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const body = request.postDataJSON() as Record<string, unknown> | null;
    api.calls.push({ path, body });
    switch (path) {
      case '/api/users/refresh':
        return api.signedIn
          ? json(route, 200, session())
          : json(route, 401, problem('session-expired', 401));
      case '/api/users/login': {
        const answer = api.logins[String(body?.['password'])] ?? {
          status: 401,
          body: problem('invalid-credentials', 401),
        };
        if (answer.status === 200) {
          api.signedIn = true;
        }
        return json(route, answer.status, answer.body, answer.headers);
      }
      case '/api/users/logout':
        api.signedIn = false;
        return route.fulfill({ status: 204 });
      case '/api/users/register':
      case '/api/users/verify-email/resend':
      case '/api/users/password/forgot':
        return route.fulfill({ status: 202 });
      case '/api/users/password/reset':
        if (body?.['token'] !== 'valid-reset-token') {
          return json(route, 400, problem('reset-token-invalid', 400));
        }
        api.signedIn = true;
        return json(route, 200, session());
      case '/api/users/verify-email':
        return body?.['token'] === 'valid-token'
          ? route.fulfill({ status: 204 })
          : json(route, 400, problem('verification-token-invalid', 400));
      default:
        return json(route, 503, problem('service-unavailable', 503));
    }
  });
  return api;
}
