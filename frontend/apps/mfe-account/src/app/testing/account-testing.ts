import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { provideRouter, Routes } from '@angular/router';
import { authInterceptor } from '@soukly/auth';
import { i18nTesting, Lang } from '@soukly/i18n';
import ar from '../../../public/i18n/account/ar.json';
import en from '../../../public/i18n/account/en.json';
import fr from '../../../public/i18n/account/fr.json';

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
