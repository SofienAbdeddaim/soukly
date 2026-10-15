import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Header } from './header';

describe('Header', () => {
  it('shows the logo, the language slot and the two actions', async () => {
    TestBed.configureTestingModule({ imports: [Header], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('header a')?.textContent).toContain('Soukly');
    expect(element.querySelector('[data-slot=language-switcher]')).not.toBeNull();
    expect(element.querySelector('a[href="/account/login"]')?.textContent).toContain(
      'Se connecter',
    );
    expect(element.querySelector('a[href="/publish/new"]')?.textContent).toContain(
      'Déposer une annonce',
    );
  });
});
