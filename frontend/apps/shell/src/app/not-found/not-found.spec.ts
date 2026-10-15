import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotFound } from './not-found';

describe('NotFound', () => {
  it('links back to the home page', async () => {
    TestBed.configureTestingModule({ imports: [NotFound], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(NotFound);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Page introuvable');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/');
  });
});
