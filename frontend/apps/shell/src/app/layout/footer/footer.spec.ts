import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { shellI18nTesting } from '../../testing/shell-i18n-testing';
import { Footer } from './footer';

describe('Footer', () => {
  it('links to the three footer pages, under the language prefix', async () => {
    TestBed.configureTestingModule({
      imports: [Footer, shellI18nTesting()],
      providers: [provideRouter([])],
    });
    const fixture = TestBed.createComponent(Footer);
    await fixture.whenStable();
    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('nav a')).map(
      (link) => [link.textContent?.trim(), link.getAttribute('href')],
    );

    expect(links).toEqual([
      ['Aide', '/fr/help'],
      ["Conditions d'utilisation", '/fr/terms'],
      ['Confidentialité', '/fr/privacy'],
    ]);
  });
});
