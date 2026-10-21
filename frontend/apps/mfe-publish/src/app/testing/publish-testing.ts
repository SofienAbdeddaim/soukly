import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Routes } from '@angular/router';
import { authInterceptor } from '@soukly/auth';
import { i18nTesting, Lang, LocaleService } from '@soukly/i18n';
import { Listing } from '@soukly/listing-data';
import { DraftStore } from '../draft/draft-store';
import ar from '../../../public/i18n/publish/ar.json';
import en from '../../../public/i18n/publish/en.json';
import fr from '../../../public/i18n/publish/fr.json';

/** Transloco for the unit tests of mfe-publish, with the real "publish" translations. */
export function publishI18nTesting(lang: Lang = 'fr') {
  return i18nTesting({ 'publish/fr': fr, 'publish/ar': ar, 'publish/en': en }, lang);
}

@Component({ template: '' })
export class BlankPage {}

/** Router, faked back end and, for the steps of a draft, the store of the draft page. */
export function publishTestingProviders(routes: Routes = []) {
  return [
    DraftStore,
    provideRouter([...routes, { path: '**', component: BlankPage }]),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideHttpClientTesting(),
  ];
}

/** Lets the promises in progress settle. */
export const settle = () => new Promise((resolve) => setTimeout(resolve));

/** Loads a draft in the store, as the draft page does, and sets the language of the page. */
export async function loadDraft(listing: Listing, lang: Lang = 'fr'): Promise<DraftStore> {
  TestBed.inject(LocaleService).use(lang);
  const store = TestBed.inject(DraftStore);
  const loaded = store.load(listing.id);
  TestBed.inject(HttpTestingController)
    .expectOne(`/api/listings/${listing.id}`)
    .flush(listing, { headers: { ETag: `"${listing.version}"` } });
  await loaded;
  return store;
}
