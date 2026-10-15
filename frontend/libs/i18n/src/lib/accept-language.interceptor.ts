import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { LocaleService } from './locale.service';

/**
 * Sends the current language with every API call (`Accept-Language: ar`), so the back end
 * answers in that language (for example the language of the emails, AUTH-01).
 */
export const acceptLanguageInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.headers.has('Accept-Language')) {
    return next(request);
  }
  const lang = inject(LocaleService).lang();
  return next(request.clone({ setHeaders: { 'Accept-Language': lang } }));
};
