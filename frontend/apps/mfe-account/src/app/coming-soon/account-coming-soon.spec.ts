import { TestBed } from '@angular/core/testing';
import { i18nTesting, Lang } from '@soukly/i18n';
import ar from '../../../public/i18n/account/ar.json';
import en from '../../../public/i18n/account/en.json';
import fr from '../../../public/i18n/account/fr.json';
import { AccountComingSoon } from './account-coming-soon';

describe('AccountComingSoon', () => {
  async function title(lang: Lang): Promise<string> {
    TestBed.configureTestingModule({
      imports: [
        AccountComingSoon,
        i18nTesting({ 'account/fr': fr, 'account/ar': ar, 'account/en': en }, lang),
      ],
    });
    const fixture = TestBed.createComponent(AccountComingSoon);
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent?.trim() ?? '';
  }

  it('shows the waiting message of the account area in French', async () => {
    expect(await title('fr')).toBe('Espace compte Soukly, bientôt disponible');
  });

  it('shows the waiting message of the account area in Arabic', async () => {
    expect(await title('ar')).toBe('فضاء الحساب في سوقلي، قريبًا');
  });
});
