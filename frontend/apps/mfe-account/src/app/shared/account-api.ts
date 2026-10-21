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
 * Registration and email verification calls of user-service (AUTH-01). The language of the emails
 * follows the `Accept-Language` header added by `acceptLanguageInterceptor`.
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
}
