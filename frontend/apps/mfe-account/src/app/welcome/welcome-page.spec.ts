import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService, sessionResponse } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { WelcomePage } from './welcome-page';

describe('WelcomePage', () => {
  async function setup(url: string, lang: 'fr' | 'ar' = 'fr') {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting(lang)],
      providers: accountTestingProviders([
        { path: ':lang/account/welcome', component: WelcomePage },
      ]),
    });
    TestBed.inject(LocaleService).use(lang);
    const login = TestBed.inject(AuthService).login('amira@example.tn', 'secret-password');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/users/login')
      .flush(sessionResponse('token-1', true));
    await login;
    const harness = await RouterTestingHarness.create(url);
    return harness.routeNativeElement as HTMLElement;
  }

  afterEach(() => TestBed.inject(LocaleService).use('fr'));

  it('greets the member and offers Facebook and Instagram', async () => {
    const element = await setup('/fr/account/welcome');

    expect(element.textContent).toContain('Bienvenue, Amira');
    expect(element.querySelector('h1')?.textContent).toContain('Une annonce, publiée partout');
    const links = Array.from(element.querySelectorAll('a')).map((link) => [
      link.textContent?.trim(),
      link.getAttribute('href'),
    ]);
    expect(links).toEqual([
      ['Plus tard', '/fr'],
      ['Relier', '/fr/account/networks/facebook'],
      ['Relier', '/fr/account/networks/instagram'],
      ['Continuer vers Soukly', '/fr'],
    ]);
  });

  it('leads "Later" and "Continue" to the page asked for before the login', async () => {
    const element = await setup('/ar/account/welcome?returnUrl=%2Far%2Fpublish%2Fnew', 'ar');

    expect(element.querySelector('[data-testid=welcome-later]')?.getAttribute('href')).toBe(
      '/ar/publish/new',
    );
    expect(element.querySelector('[data-testid=welcome-continue]')?.textContent).toContain(
      'المتابعة إلى سوقلي',
    );
  });
});
