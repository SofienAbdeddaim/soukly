import { SessionResponse } from './session';

/** A `login` or `refresh` answer for unit tests: Amira, in French. */
export function sessionResponse(accessToken: string, firstLogin = false): SessionResponse {
  return {
    accessToken,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: '6b7f1f9e-2f1c-4a8e-9a57-3c2d8f0e1a11',
      displayName: 'Amira',
      preferredLanguage: 'fr',
    },
    firstLogin,
  };
}
