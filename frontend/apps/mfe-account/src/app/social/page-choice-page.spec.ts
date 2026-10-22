import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LocaleService } from '@soukly/i18n';
import { PageCandidate } from '@soukly/social-data';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { PageChoicePage } from './page-choice-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

const PAGES: PageCandidate[] = [
  { pageId: '1001', name: 'Sfax Auto Occasion', category: 'Concessionnaire', pictureUrl: null },
  { pageId: '1002', name: 'Amira Déco', category: 'Boutique', pictureUrl: null },
  { pageId: '1003', name: 'Cours Ben Salah', category: 'Éducation', pictureUrl: null },
];

describe('PageChoicePage', () => {
  async function setup(pages: PageCandidate[] | null = PAGES) {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting('fr')],
      providers: accountTestingProviders([
        { path: 'fr/account/social/facebook/page', component: PageChoicePage },
      ]),
    });
    TestBed.inject(LocaleService).use('fr');
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/fr/account/social/facebook/page');
    const request = http.expectOne('/api/social/facebook/page-selection');
    if (pages) {
      request.flush({ pendingSelection: pages });
    } else {
      request.flush({}, { status: 404, statusText: 'Not Found' });
    }
    await settle();
    await harness.fixture.whenStable();
    return { harness, http, element: () => harness.routeNativeElement as HTMLElement };
  }

  it('lists the Pages as radio buttons in a fieldset with a legend, the first one chosen', async () => {
    const { element } = await setup();

    expect(element().querySelector('h1')?.textContent).toContain('Choisir une Page');
    expect(element().querySelector('fieldset legend')?.textContent).toContain(
      'Vous gérez plusieurs Pages',
    );
    const radios = Array.from(
      element().querySelectorAll('input[type=radio]'),
    ) as HTMLInputElement[];
    expect(radios).toHaveLength(3);
    expect(radios[0].checked).toBe(true);
    expect(element().textContent).toContain('Amira Déco');
    expect(element().textContent).toContain('Éducation');
  });

  it('connects the chosen Page and goes back to My networks', async () => {
    const { harness, http, element } = await setup();

    const second = element().querySelectorAll('input[type=radio]')[1] as HTMLInputElement;
    second.click();
    await harness.fixture.whenStable();
    (element().querySelector('[data-testid=page-choice-submit]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    const request = http.expectOne('/api/social/facebook/page');
    expect(request.request.body).toEqual({ pageId: '1002' });
    request.flush({
      network: 'FACEBOOK',
      status: 'CONNECTED',
      displayName: 'Amira Déco',
      pictureUrl: null,
      connectedAt: '2026-10-12T09:30:00Z',
      autoShare: true,
    });
    await settle();

    expect(TestBed.inject(Router).url).toBe('/fr/account/social?facebook=connected');
  });

  it('goes back to My networks when the choice expired', async () => {
    await setup(null);

    expect(TestBed.inject(Router).url).toBe('/fr/account/social?facebook=page-selection-not-found');
  });
});
