import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthUser, SESSION_URLS, SessionResponse } from './session';

interface OpenSession {
  accessToken: string;
  user: AuthUser;
}

/**
 * Session of the visitor on the front end, shared by the shell and every remote (singleton of the
 * federation). The access token lives in memory only: never in localStorage, sessionStorage or a
 * readable cookie. The session itself is the HttpOnly refresh cookie set by user-service, so a
 * page reload restores it with `refresh`.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<OpenSession | null>(null);
  private refreshing: Promise<boolean> | null = null;
  private restoring: Promise<void> | null = null;

  /** The signed-in member, or null. */
  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);

  accessToken(): string | null {
    return this.session()?.accessToken ?? null;
  }

  /**
   * Opens a session. Rejects with the `HttpErrorResponse` of the API (`invalid-credentials`,
   * `email-not-verified`, `429`…), which the login page translates.
   */
  async login(email: string, password: string): Promise<{ firstLogin: boolean }> {
    const response = await firstValueFrom(
      this.http.post<SessionResponse>(SESSION_URLS.login, { email, password }),
    );
    this.open(response);
    return { firstLogin: response.firstLogin === true };
  }

  /**
   * Chooses a new password with the token of a reset link (AUTH-04). user-service signs out every
   * other device and opens a session for this one, exactly like `login`. Rejects with the
   * `HttpErrorResponse` of the API (`reset-token-invalid`, `validation-failed`, `429`…).
   */
  async resetPassword(token: string, newPassword: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<SessionResponse>(SESSION_URLS.resetPassword, { token, newPassword }),
    );
    this.open(response);
  }

  /**
   * Renews the access token with the refresh cookie; resolves to false, with the session cleared,
   * when the cookie is missing, expired or revoked. Calls made while a refresh is running share it:
   * two refreshes in parallel with the same cookie would look like a stolen token to user-service,
   * which then revokes the session.
   */
  refresh(): Promise<boolean> {
    this.refreshing ??= firstValueFrom(this.http.post<SessionResponse>(SESSION_URLS.refresh, null))
      .then(
        (response) => {
          this.open(response);
          return true;
        },
        () => {
          this.clear();
          return false;
        },
      )
      .finally(() => (this.refreshing = null));
    return this.refreshing;
  }

  /** Restores the session of a previous visit, once, when the app starts. */
  restore(): Promise<void> {
    this.restoring ??= this.refresh().then(() => undefined);
    return this.restoring;
  }

  /** Resolves when the session restored at start-up is known (immediately if none was started). */
  whenRestored(): Promise<void> {
    return this.restoring ?? Promise.resolve();
  }

  /** Closes the session on the server (revokes the cookie) and in this page, even if the call fails. */
  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>(SESSION_URLS.logout, null));
    } catch {
      // The access token is forgotten anyway; the cookie expires on its own.
    } finally {
      this.clear();
    }
  }

  /**
   * Updates the signed-in member after a change of their profile (AUTH-05), so that the header shows
   * the new name at once. Does nothing without a session.
   */
  updateUser(changes: Partial<Omit<AuthUser, 'id'>>): void {
    this.session.update((session) =>
      session ? { ...session, user: { ...session.user, ...changes } } : session,
    );
  }

  /** Forgets the session in this page only. */
  clear(): void {
    this.session.set(null);
  }

  private open(response: SessionResponse): void {
    this.session.set({ accessToken: response.accessToken, user: response.user });
  }
}
