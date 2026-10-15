import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { LANG_STORAGE_KEY, LocaleService } from '@soukly/i18n';
import { shellI18nTesting } from '../../testing/shell-i18n-testing';
import { LanguageSwitcher } from './language-switcher';

@Component({ template: '' })
class Page {}

describe('LanguageSwitcher', () => {
  async function setup(url = '/fr/account/login?next=%2Fpublish') {
    TestBed.configureTestingModule({
      imports: [LanguageSwitcher, shellI18nTesting()],
      providers: [provideRouter([{ path: '**', component: Page }])],
    });
    await TestBed.inject(Router).navigateByUrl(url);
    const fixture = TestBed.createComponent(LanguageSwitcher);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const toggle = element.querySelector('button') as HTMLButtonElement;
    const menu = element.querySelector('ul') as HTMLUListElement;
    return { fixture, element, toggle, menu };
  }

  afterEach(() => localStorage.clear());

  it('names the current language in its own language, closed by default', async () => {
    const { toggle, menu } = await setup();

    expect(toggle.textContent).toContain('Changer de langue');
    expect(toggle.textContent).toContain('Français');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(menu.hidden).toBe(true);
  });

  it('lists the three languages, each written in its own language, as links to the same page', async () => {
    const { fixture, toggle, menu } = await setup();

    toggle.click();
    await fixture.whenStable();

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(menu.hidden).toBe(false);
    const links = Array.from(menu.querySelectorAll('a')).map((link) => ({
      name: link.textContent?.trim(),
      lang: link.getAttribute('lang'),
      href: link.getAttribute('href'),
      current: link.getAttribute('aria-current'),
    }));
    expect(links).toEqual([
      { name: 'Français', lang: 'fr', href: '/fr/account/login?next=%2Fpublish', current: 'true' },
      { name: 'العربية', lang: 'ar', href: '/ar/account/login?next=%2Fpublish', current: null },
      { name: 'English', lang: 'en', href: '/en/account/login?next=%2Fpublish', current: null },
    ]);
  });

  it('navigates without reloading, remembers the choice and closes', async () => {
    const { fixture, toggle, menu } = await setup();
    toggle.click();
    await fixture.whenStable();

    (menu.querySelector('a[lang=en]') as HTMLAnchorElement).click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/en/account/login?next=%2Fpublish');
    expect(localStorage.getItem(LANG_STORAGE_KEY)).toBe('en');
    expect(menu.hidden).toBe(true);
  });

  it('closes on Escape and gives the focus back to the button', async () => {
    const { fixture, element, toggle, menu } = await setup();
    toggle.click();
    await fixture.whenStable();

    element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();

    expect(menu.hidden).toBe(true);
    expect(document.activeElement).toBe(toggle);
  });

  it('shows the current language after a switch', async () => {
    const { fixture, toggle } = await setup('/ar');
    TestBed.inject(LocaleService).use('ar');
    await fixture.whenStable();

    expect(toggle.textContent).toContain('العربية');
  });
});
