import { Lang } from '@soukly/i18n';

/** Signed-in member, as returned by `login` and `refresh` (AUTH-02). No email or phone number. */
export interface AuthUser {
  id: string;
  displayName: string;
  preferredLanguage: Lang;
}

/** Body of a successful `login`, `refresh` or `resetPassword`; the refresh token stays in its HttpOnly cookie. */
export interface SessionResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Validity of the access token, in seconds. */
  expiresIn: number;
  user: AuthUser;
  /** True on the very first login of the account only (AUTH-03), never on a refresh. */
  firstLogin: boolean;
}

/** Prefix of every call to the Soukly API, on the origin of the page (the gateway, or the dev proxy). */
export const API_PREFIX = '/api';

/** Session endpoints of user-service, through the gateway. */
export const SESSION_URLS = {
  login: `${API_PREFIX}/users/login`,
  refresh: `${API_PREFIX}/users/refresh`,
  logout: `${API_PREFIX}/users/logout`,
  /** Forgotten password (AUTH-04): answers like `login`. */
  resetPassword: `${API_PREFIX}/users/password/reset`,
} as const;
