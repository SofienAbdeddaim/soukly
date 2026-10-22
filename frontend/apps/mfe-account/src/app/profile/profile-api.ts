import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_PREFIX } from '@soukly/auth';
import { Lang } from '@soukly/i18n';
import { firstValueFrom } from 'rxjs';

/** Body of `GET /users/me` (AUTH-05). */
export interface Profile {
  id: string;
  email: string;
  /** Address waiting for its confirmation link, or null. */
  pendingEmail: string | null;
  displayName: string;
  /** E.164 Tunisian number, for example +21620123456. */
  phone: string;
  /** Language of the emails; the interface follows the language of the URL. */
  preferredLanguage: Lang;
  createdAt: string;
}

export type ProfileChanges = Partial<Pick<Profile, 'displayName' | 'phone' | 'preferredLanguage'>>;

const USERS = `${API_PREFIX}/users`;
const MERGE_PATCH = new HttpHeaders({ 'Content-Type': 'application/merge-patch+json' });

/**
 * Profile, email and password changes and deletion of the account (AUTH-05). Every call but
 * `confirmEmail` needs the session; a wrong current password is `400 current-password-invalid`,
 * repeated failures `429` with `Retry-After` (the login throttling).
 */
@Injectable({ providedIn: 'root' })
export class ProfileApi {
  private readonly http = inject(HttpClient);

  get(): Promise<Profile> {
    return firstValueFrom(this.http.get<Profile>(`${USERS}/me`));
  }

  /** JSON Merge Patch: only the members sent change. Answers the whole profile. */
  update(changes: ProfileChanges): Promise<Profile> {
    return firstValueFrom(
      this.http.patch<Profile>(`${USERS}/me`, changes, { headers: MERGE_PATCH }),
    );
  }

  /** `204`; every other device is signed out, this one keeps its session. */
  changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${USERS}/me/password`, { currentPassword, newPassword }),
    );
  }

  /**
   * `202` whether the new address is free or not (the answer never reveals an account): a link
   * valid 24 hours goes to the new address, a notice to the current one.
   */
  requestEmailChange(newEmail: string, currentPassword: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${USERS}/me/email`, { newEmail, currentPassword }));
  }

  cancelEmailChange(): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${USERS}/me/email-change`));
  }

  /**
   * Public: the link can be opened on another device. `400 email-change-token-invalid` for an
   * unknown, expired or used token, `409 email-taken` when the address was taken meanwhile.
   */
  confirmEmail(token: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${USERS}/email/confirm`, { token }));
  }

  /** `204`; the account can no longer sign in, the refresh cookie is cleared. */
  deleteAccount(currentPassword: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${USERS}/me`, { body: { currentPassword } }));
  }
}
