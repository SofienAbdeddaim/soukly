import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { acceptLanguageInterceptor } from './accept-language.interceptor';
import { LocaleService } from './locale.service';

describe('acceptLanguageInterceptor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([acceptLanguageInterceptor])),
        provideHttpClientTesting(),
      ],
    });
  });

  afterEach(() => TestBed.inject(LocaleService).use('fr'));

  it('sends the current language with every API call', () => {
    const http = TestBed.inject(HttpClient);
    const backend = TestBed.inject(HttpTestingController);

    TestBed.inject(LocaleService).use('ar');
    http.get('/api/listings').subscribe();
    expect(backend.expectOne('/api/listings').request.headers.get('Accept-Language')).toBe('ar');

    http.get('/api/users/me', { headers: { 'Accept-Language': 'en' } }).subscribe();
    expect(backend.expectOne('/api/users/me').request.headers.get('Accept-Language')).toBe('en');
    backend.verify();
  });
});
