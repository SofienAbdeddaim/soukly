import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_PREFIX } from '@soukly/auth';
import { firstValueFrom } from 'rxjs';

export interface RegisterRequest {
  displayName: string;
  email: string;
  /** E.164 Tunisian number, for example +21620123456. */
  phone: string;
  acceptTerms: boolean;
  password: string;
}

const USERS = `${API_PREFIX}/users`;

/**
 * Registration, email verification (AUTH-01) and forgotten password (AUTH-04) calls of
 * user-service. The language of the emails follows the `Accept-Language` header added by
 * `acceptLanguageInterceptor` (the reset emails use the language of the account).
 */
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);

  /** `202` whether the email is new or not: the answer never reveals an account. */
  register(request: RegisterRequest): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${USERS}/register`, request));
  }

  /** Rejects with `400 verification-token-invalid` for an unknown, expired or used token. */
  verifyEmail(token: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${USERS}/verify-email`, { token }));
  }

  /** `202` whatever the state of the account (at most 3 emails an hour are actually sent). */
  resendVerification(email: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${USERS}/verify-email/resend`, { email }));
  }

  /**
   * Asks for a password reset link (AUTH-04). `202` whether the email has an account or not (at
   * most 3 links an hour are actually sent); the new password itself goes through `AuthService`.
   */
  forgotPassword(email: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${USERS}/password/forgot`, { email }));
  }
}
