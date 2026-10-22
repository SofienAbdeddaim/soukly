import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { shellI18nTesting } from '../../testing/shell-i18n-testing';
import { Footer } from './footer';

describe('Footer', () => {
  it('links to the five legal pages, under the language prefix', async () => {
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
      ['Conditions', '/fr/legal/terms'],
      ['Confidentialité', '/fr/legal/privacy'],
      ['Règles de publication', '/fr/legal/posting-rules'],
      ['Mentions légales', '/fr/legal/notice'],
      ['Supprimer mes données', '/fr/legal/data-deletion'],
    ]);
  });
});
