import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, RedirectFunction, Router, Routes } from '@angular/router';
import { LangActivation } from './lang-activation';
import { isLang, looksLikeLangCode } from './languages';
import { LocaleService } from './locale.service';

/** The `:lang` route only matches a supported language: "/fr/…", "/ar/…", "/en/…". */
export const langMatchGuard: CanMatchFn = (_route, segments) => isLang(segments[0]?.path);

/** Activates the language of the URL prefix before its pages are rendered. */
export const langGuard: CanActivateFn = async (route) => {
  const lang = route.paramMap.get('lang');
  if (isLang(lang)) {
    await inject(LangActivation).activate(lang);
  }
  return true;
};

/**
 * Any URL without a supported prefix: "/" and "/account/login" get the preferred language
 * (remembered choice, browser languages, French), "/de/publish/new" becomes "/fr/publish/new".
 * The rest of the path, the query parameters and the fragment are kept.
 */
export const redirectToLang: RedirectFunction = (route) => {
  const segments = route.url.map((segment) => segment.path);
  const lang = inject(LocaleService).langForSegment(segments[0]);
  const rest = looksLikeLangCode(segments[0]) ? segments.slice(1) : segments;
  return inject(Router).createUrlTree(['/', lang, ...rest], {
    queryParams: route.queryParams,
    fragment: route.fragment ?? undefined,
  });
};

/** Puts routes under the language prefix, and redirects every other URL to a prefixed one. */
export function withLangPrefix(children: Routes): Routes {
  return [
    { path: ':lang', canMatch: [langMatchGuard], canActivate: [langGuard], children },
    { path: '**', redirectTo: redirectToLang },
  ];
}
