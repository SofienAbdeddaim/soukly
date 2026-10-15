import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { shellI18nTesting } from '../testing/shell-i18n-testing';
import { NotFound } from './not-found';

describe('NotFound', () => {
  it('links back to the home page of the current language', async () => {
    TestBed.configureTestingModule({
      imports: [NotFound, shellI18nTesting()],
      providers: [provideRouter([])],
    });
    const fixture = TestBed.createComponent(NotFound);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Page introuvable');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/fr');
  });
});
