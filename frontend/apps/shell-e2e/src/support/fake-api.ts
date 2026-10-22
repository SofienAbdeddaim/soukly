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
  /** Profile of the signed-in member (AUTH-05), changed by the profile calls. */
  profile: FakeProfile;
}

export interface FakeProfile {
  id: string;
  email: string;
  pendingEmail: string | null;
  displayName: string;
  phone: string;
  preferredLanguage: string;
  createdAt: string;
}

/** Current password of Amira for the profile calls (AUTH-05). */
export const CURRENT_PASSWORD = 'Jasmin-de-Carthage-2026';

export function profile(): FakeProfile {
  return {
    id: '6b7f1f9e-2f1c-4a8e-9a57-3c2d8f0e1a11',
    email: 'amira@example.tn',
    pendingEmail: null,
    displayName: 'Amira B.',
    phone: '+21620123456',
    preferredLanguage: 'fr',
    createdAt: '2026-03-14T10:00:00Z',
  };
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
  options: Partial<Pick<FakeAccountApi, 'signedIn' | 'logins' | 'profile'>> = {},
): Promise<FakeAccountApi> {
  const api: FakeAccountApi = {
    signedIn: false,
    logins: {},
    calls: [],
    profile: profile(),
    ...options,
  };
  const json = (route: Route, status: number, body?: unknown, headers?: Record<string, string>) =>
    route.fulfill({
      status,
      headers,
      contentType: status >= 400 ? 'application/problem+json' : 'application/json',
      body: body === undefined ? '' : JSON.stringify(body),
    });

  const wrongPassword = (route: Route) =>
    json(route, 400, problem('current-password-invalid', 400));

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
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
      // Profile and deletion of the account (AUTH-05)
      case '/api/users/me':
        if (method === 'GET') {
          return json(route, 200, api.profile);
        }
        if (method === 'PATCH') {
          api.profile = { ...api.profile, ...body };
          return json(route, 200, api.profile);
        }
        if (body?.['currentPassword'] !== CURRENT_PASSWORD) {
          return wrongPassword(route);
        }
        api.signedIn = false;
        return route.fulfill({ status: 204 });
      case '/api/users/me/password':
        return body?.['currentPassword'] === CURRENT_PASSWORD
          ? route.fulfill({ status: 204 })
          : wrongPassword(route);
      case '/api/users/me/email':
        if (body?.['currentPassword'] !== CURRENT_PASSWORD) {
          return wrongPassword(route);
        }
        api.profile = { ...api.profile, pendingEmail: String(body?.['newEmail']) };
        return route.fulfill({ status: 202 });
      case '/api/users/me/email-change':
        api.profile = { ...api.profile, pendingEmail: null };
        return route.fulfill({ status: 204 });
      case '/api/users/email/confirm':
        if (body?.['token'] !== 'valid-email-token') {
          return json(route, 400, problem('email-change-token-invalid', 400));
        }
        api.profile = {
          ...api.profile,
          email: api.profile.pendingEmail ?? api.profile.email,
          pendingEmail: null,
        };
        return route.fulfill({ status: 204 });
      default:
        return json(route, 503, problem('service-unavailable', 503));
    }
  });
  return api;
}
