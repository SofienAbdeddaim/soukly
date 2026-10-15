import { TestBed } from '@angular/core/testing';
import { i18nTesting, Lang } from '@soukly/i18n';
import ar from '../../../public/i18n/catalog/ar.json';
import en from '../../../public/i18n/catalog/en.json';
import fr from '../../../public/i18n/catalog/fr.json';
import { CatalogComingSoon } from './catalog-coming-soon';

describe('CatalogComingSoon', () => {
  async function title(lang: Lang): Promise<string> {
    TestBed.configureTestingModule({
      imports: [
        CatalogComingSoon,
        i18nTesting({ 'catalog/fr': fr, 'catalog/ar': ar, 'catalog/en': en }, lang),
      ],
    });
    const fixture = TestBed.createComponent(CatalogComingSoon);
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent?.trim() ?? '';
  }

  it('shows the waiting message of the catalogue in French', async () => {
    expect(await title('fr')).toBe('Catalogue Soukly, bientôt disponible');
  });

  it('shows the waiting message of the catalogue in Arabic', async () => {
    expect(await title('ar')).toBe('كتالوج سوقلي، قريبًا');
  });
});
