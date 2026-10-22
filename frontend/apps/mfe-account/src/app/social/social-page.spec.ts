import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LocaleService } from '@soukly/i18n';
import { ExternalNavigation, SocialConnection } from '@soukly/social-data';
import { ToastService } from '@soukly/ui';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { SocialPage } from './social-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

const SFAX_AUTO: SocialConnection = {
  network: 'FACEBOOK',
  status: 'CONNECTED',
  displayName: 'Sfax Auto Occasion',
  pictureUrl: null,
  connectedAt: '2026-10-12T09:30:00Z',
};

describe('SocialPage', () => {
  const go = vi.fn();

  async function setup(
    url = '/fr/account/social',
    connections: SocialConnection[] = [],
    lang: 'fr' | 'ar' = 'fr',
  ) {
    go.mockReset();
    TestBed.configureTestingModule({
      imports: [accountI18nTesting(lang)],
      providers: [
        ...accountTestingProviders([{ path: `${lang}/account/social`, component: SocialPage }]),
        { provide: ExternalNavigation, useValue: { go } },
      ],
    });
    TestBed.inject(LocaleService).use(lang);
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    http.expectOne('/api/social/connections').flush(connections);
    await settle();
    await harness.fixture.whenStable();
    const element = () => harness.routeNativeElement as HTMLElement;
    const card = () => element().querySelector('[data-testid=facebook-card]') as HTMLElement;
    return { harness, http, element, card };
  }

  it('shows the Facebook card "Not connected" and Instagram coming soon', async () => {
    const { element, card } = await setup();

    expect(element().querySelector('h1')?.textContent).toContain('Mes réseaux');
    expect(card().dataset['state']).toBe('not-connected');
    expect(card().textContent).toContain('Non reliée');
    expect(card().textContent).toContain('Un profil personnel ne suffit pas');
    expect(element().querySelector('[data-testid=instagram-card]')?.textContent).toContain(
      'Bientôt disponible',
    );
  });

  it('starts a connection in the language of the page and opens the Facebook dialog', async () => {
    const { harness, http, card } = await setup('/ar/account/social', [], 'ar');

    (card().querySelector('[data-testid=facebook-connect]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/social/facebook/connect');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ lang: 'ar' });
    request.flush({ authorizeUrl: 'https://www.facebook.com/v26.0/dialog/oauth?state=abc' });
    await settle();
    await harness.fixture.whenStable();

    expect(go).toHaveBeenCalledWith('https://www.facebook.com/v26.0/dialog/oauth?state=abc');
  });

  it('shows the connected Page with its date, and the success notice after Facebook', async () => {
    const { element, card } = await setup('/fr/account/social?facebook=connected', [SFAX_AUTO]);

    expect(card().dataset['state']).toBe('connected');
    expect(card().textContent).toContain('Sfax Auto Occasion');
    expect(card().textContent).toContain('Page Facebook · reliée le 12 oct.');
    expect(element().querySelector('[data-testid=facebook-connected]')?.textContent).toContain(
      'Page reliée.',
    );
    // Read once, then removed from the address
    expect(TestBed.inject(Router).url).toBe('/fr/account/social');
  });

  it('offers "Connect again" when Facebook refused the token', async () => {
    const { card } = await setup('/fr/account/social', [
      { ...SFAX_AUTO, status: 'NEEDS_RECONNECT' },
    ]);

    expect(card().dataset['state']).toBe('needs-reconnect');
    expect(card().textContent).toContain("Facebook a retiré l'autorisation");
    expect(card().querySelector('[data-testid=facebook-reconnect]')?.textContent).toContain(
      'Relier à nouveau',
    );
  });

  it.each([
    ['oauth-cancelled', 'Vous avez annulé la connexion', 'Réessayer'],
    [
      'oauth-permission-missing',
      "Soukly a besoin de l'autorisation de publier sur votre Page",
      'Réessayer',
    ],
    ['oauth-state-invalid', 'Ce lien de connexion a expiré', 'Recommencer'],
    ['something-else', "La connexion n'a pas abouti", 'Réessayer'],
  ])('explains the failure %s with its action', async (failure, title, action) => {
    const { element, card } = await setup(`/fr/account/social?facebook=${failure}`);

    const panel = element().querySelector('[data-testid=facebook-failure]') as HTMLElement;
    expect(panel.getAttribute('role')).toBe('alert');
    expect(panel.textContent).toContain(title);
    expect(panel.querySelector('[data-testid=facebook-retry]')?.textContent).toContain(action);
    expect(card().dataset['state']).toBe('not-connected');
  });

  it('explains how to create a Page when the member manages none', async () => {
    const { element } = await setup('/fr/account/social?facebook=facebook-no-page');

    const panel = element().querySelector('[data-testid=facebook-failure]') as HTMLElement;
    expect(panel.textContent).toContain('Vous ne gérez aucune Page Facebook');
    const help = panel.querySelector('[data-testid=facebook-create-page]') as HTMLAnchorElement;
    expect(help.href).toBe('https://www.facebook.com/pages/create');
    expect(help.target).toBe('_blank');
    expect(help.rel).toContain('noopener');
  });

  it('disconnects after the confirmation and shows "Not connected"', async () => {
    const { harness, http, card } = await setup('/fr/account/social', [SFAX_AUTO]);
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');

    (card().querySelector('[data-testid=facebook-disconnect]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    const dialog = document.querySelector('[data-testid=disconnect-dialog]') as HTMLElement;
    expect(dialog.textContent).toContain('Déconnecter cette Page ?');
    expect(dialog.textContent).toContain('Sfax Auto Occasion');
    (dialog.querySelector('[data-testid=disconnect-confirm]') as HTMLButtonElement).click();
    await settle();
    const request = http.expectOne('/api/social/facebook');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    await harness.fixture.whenStable();

    expect(card().dataset['state']).toBe('not-connected');
    expect(show).toHaveBeenCalledWith(expect.objectContaining({ message: 'Page déconnectée.' }));
  });

  it('keeps the Page when the disconnection is cancelled', async () => {
    const { harness, http, card } = await setup('/fr/account/social', [SFAX_AUTO]);

    (card().querySelector('[data-testid=facebook-disconnect]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    (document.querySelector('[data-testid=disconnect-cancel]') as HTMLButtonElement).click();
    await settle();

    http.expectNone('/api/social/facebook');
    expect(card().dataset['state']).toBe('connected');
  });
});
