import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { provideRouter, Routes } from '@angular/router';
import { authInterceptor } from '@soukly/auth';
import { i18nTesting, Lang } from '@soukly/i18n';
import ar from '../../../public/i18n/account/ar.json';
import en from '../../../public/i18n/account/en.json';
import fr from '../../../public/i18n/account/fr.json';
import { Profile } from '../profile/profile-api';

/** Transloco for the unit tests of mfe-account, with the real "account" translations. */
export function accountI18nTesting(lang: Lang = 'fr') {
  return i18nTesting({ 'account/fr': fr, 'account/ar': ar, 'account/en': en }, lang);
}

@Component({ template: '' })
export class BlankPage {}

/** Router with the pages the account screens lead to, and a faked back end. */
export function accountTestingProviders(routes: Routes = []) {
  return [
    provideRouter([...routes, { path: '**', component: BlankPage }]),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideHttpClientTesting(),
  ];
}

/** Profile of a member, as `GET /users/me` answers it (AUTH-05). */
export const PROFILE: Profile = {
  id: 'c3a1b2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d',
  email: 'amira@example.tn',
  pendingEmail: null,
  displayName: 'Amira Ben Salah',
  phone: '+21620123456',
  preferredLanguage: 'fr',
  createdAt: '2026-03-14T10:00:00Z',
};
