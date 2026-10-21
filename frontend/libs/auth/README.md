# @soukly/auth

Session of the visitor on the front end (AUTH-03), shared by the shell and every remote: Native Federation loads it once (`@soukly/*` singletons, `federation.shared.mjs`), so all of them see the same member.

```bash
cd frontend
pnpm nx test auth    # AuthService, interceptor, guards, returnUrl and API problem helpers
pnpm nx lint auth
```

## Setup

In the shell (and in a remote launched alone):

```ts
provideHttpClient(withFetch(), withInterceptors([acceptLanguageInterceptor, authInterceptor])),
provideAuth(), // restores the session of a previous visit at start-up
```

## What it holds

| Export                                                         | Role                                                                                                                                                                                 |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `AuthService`                                                  | signals `user` and `isAuthenticated`; `login(email, password)` (resolves `{ firstLogin }`), `refresh()`, `logout()`, `restore()`/`whenRestored()`                                    |
| `authInterceptor`                                              | for `/api/**` on the origin of the page: `withCredentials`, `Authorization: Bearer`, one refresh then one replay on a `401`                                                          |
| `authGuard`                                                    | pages for members only: a visitor goes to `/{lang}/account/login?returnUrl=<requested page>`                                                                                         |
| `guestGuard`                                                   | login and registration pages: a member goes to the `returnUrl` or home                                                                                                               |
| `provideAuth()`                                                | app initializer that restores the session (waits at most 3 s before the first render; guards always wait for the answer)                                                             |
| `safeReturnUrl()`                                              | keeps a `returnUrl` only if it is an internal path: starts with `/`, no `//`, no backslash, no control character (no open redirect)                                                  |
| `problemCode()`, `problemFieldErrors()`, `retryAfterSeconds()` | read the `application/problem+json` errors of the API (`urn:soukly:problem:<slug>`, `errors: [{ field, code }]`, `Retry-After`); the `title` and `detail` of the API are never shown |
| `sessionResponse()`                                            | a `login`/`refresh` answer for unit tests                                                                                                                                            |

## Rules

- **The access token stays in memory** (a signal of `AuthService`): never in `localStorage`, `sessionStorage` or a readable cookie. A reload loses it; the HttpOnly refresh cookie (`soukly_rt`, `Path=/api/users`, `SameSite=Strict`) brings it back with a silent `POST /api/users/refresh` at start-up.
- **One refresh at a time**: user-service treats a refresh token presented twice as stolen and revokes the session (AUTH-02). Calls that fail together with a `401` wait for the same refresh, then are replayed once each. A call replayed after a refresh is never refreshed again (no loop), and the session endpoints (`login`, `refresh`, `logout`, `register`, `verify-email`) never trigger one.
- **Expired session**: when the refresh fails, the session is cleared; if the current page is behind `authGuard`, the visitor is sent to the login page with that page as `returnUrl`.
- The token is only sent to relative `/api/...` URLs, never to another origin.
