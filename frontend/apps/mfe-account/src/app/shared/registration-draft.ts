import { Injectable, signal } from '@angular/core';

/** What the visitor typed in the registration form, except the password. */
export interface RegistrationDraft {
  displayName: string;
  email: string;
  phone: string;
  acceptTerms: boolean;
}

/**
 * Keeps the last registration in memory, so that "Change the email address" on the check-email
 * page brings back a filled-in form. Never stored in the browser, and never holds the password.
 */
@Injectable({ providedIn: 'root' })
export class RegistrationDrafts {
  private readonly last = signal<RegistrationDraft | null>(null);

  readonly draft = this.last.asReadonly();

  save(draft: RegistrationDraft): void {
    this.last.set(draft);
  }
}
