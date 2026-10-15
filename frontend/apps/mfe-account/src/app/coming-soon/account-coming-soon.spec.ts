import { TestBed } from '@angular/core/testing';
import { AccountComingSoon } from './account-coming-soon';

describe('AccountComingSoon', () => {
  it('shows the waiting message of the account area', async () => {
    TestBed.configureTestingModule({ imports: [AccountComingSoon] });
    const fixture = TestBed.createComponent(AccountComingSoon);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe(
      'Espace compte Soukly, bientôt disponible',
    );
  });
});
