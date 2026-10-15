import { TestBed } from '@angular/core/testing';
import { CatalogComingSoon } from './catalog-coming-soon';

describe('CatalogComingSoon', () => {
  it('shows the waiting message of the catalog', async () => {
    TestBed.configureTestingModule({ imports: [CatalogComingSoon] });
    const fixture = TestBed.createComponent(CatalogComingSoon);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe(
      'Catalogue Soukly, bientôt disponible',
    );
  });
});
