import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Footer } from './footer';

describe('Footer', () => {
  it('links to the three footer pages', async () => {
    TestBed.configureTestingModule({ imports: [Footer], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Footer);
    await fixture.whenStable();
    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('nav a')).map(
      (link) => link.textContent?.trim(),
    );

    expect(links).toEqual(['Aide', "Conditions d'utilisation", 'Confidentialité']);
  });
});
