import { TestBed } from '@angular/core/testing';
import { i18nTesting, Lang } from '@soukly/i18n';
import ar from '../../../public/i18n/publish/ar.json';
import en from '../../../public/i18n/publish/en.json';
import fr from '../../../public/i18n/publish/fr.json';
import { PublishComingSoon } from './publish-coming-soon';

describe('PublishComingSoon', () => {
  async function title(lang: Lang): Promise<string> {
    TestBed.configureTestingModule({
      imports: [
        PublishComingSoon,
        i18nTesting({ 'publish/fr': fr, 'publish/ar': ar, 'publish/en': en }, lang),
      ],
    });
    const fixture = TestBed.createComponent(PublishComingSoon);
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent?.trim() ?? '';
  }

  it('shows the waiting message of the publishing area in French', async () => {
    expect(await title('fr')).toBe("Dépôt d'annonces Soukly, bientôt disponible");
  });

  it('shows the waiting message of the publishing area in Arabic', async () => {
    expect(await title('ar')).toBe('نشر الإعلانات في سوقلي، قريبًا');
  });
});
