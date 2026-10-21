import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LocaleService } from '@soukly/i18n';
import { TEST_ID } from '@soukly/listing-data';
import { publishI18nTesting } from '../testing/publish-testing';
import { DonePage } from './done-page';

describe('DonePage', () => {
  it('says the listing is online, with a link to it and to a new listing', async () => {
    TestBed.configureTestingModule({
      imports: [publishI18nTesting('fr')],
      providers: [provideRouter([])],
    });
    TestBed.inject(LocaleService).use('fr');
    const fixture = TestBed.createComponent(DonePage);
    fixture.componentRef.setInput('id', TEST_ID);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Votre annonce est en ligne');
    expect(
      [...element.querySelectorAll('a')].map((link) => [
        link.textContent?.trim(),
        link.getAttribute('href'),
      ]),
    ).toEqual([
      ["Voir l'annonce", `/fr/listings/${TEST_ID}`],
      ['Déposer une autre annonce', '/fr/publish/new'],
    ]);
  });
});
