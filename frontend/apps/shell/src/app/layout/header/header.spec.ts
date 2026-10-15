import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LocaleService } from '@soukly/i18n';
import { shellI18nTesting } from '../../testing/shell-i18n-testing';
import { Header } from './header';

describe('Header', () => {
  async function setup(lang: 'fr' | 'ar' = 'fr') {
    TestBed.configureTestingModule({
      imports: [Header, shellI18nTesting(lang)],
      providers: [provideRouter([])],
    });
    TestBed.inject(LocaleService).use(lang);
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => TestBed.inject(LocaleService).use('fr'));

  it('shows the logo, the language switcher and the two actions, under the language prefix', async () => {
    const element = await setup();

    expect(element.querySelector('header a')?.textContent).toContain('Soukly');
    expect(element.querySelector('header a')?.getAttribute('href')).toBe('/fr');
    expect(element.querySelector('app-language-switcher')).not.toBeNull();
    expect(element.querySelector('a[href="/fr/account/login"]')?.textContent).toContain(
      'Se connecter',
    );
    expect(element.querySelector('a[href="/fr/publish/new"]')?.textContent).toContain(
      'Déposer une annonce',
    );
  });

  it('is in Arabic under /ar', async () => {
    const element = await setup('ar');

    expect(element.querySelector('a[href="/ar/account/login"]')?.textContent).toContain(
      'تسجيل الدخول',
    );
    expect(element.querySelector('a[href="/ar/publish/new"]')?.textContent).toContain(
      'أضف إعلانًا',
    );
  });
});
