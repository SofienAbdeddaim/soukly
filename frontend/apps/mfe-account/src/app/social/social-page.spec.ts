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
  autoShare: true,
};

const SFAX_INSTAGRAM: SocialConnection = {
  network: 'INSTAGRAM',
  status: 'CONNECTED',
  displayName: '@sfax.auto',
  pictureUrl: null,
  connectedAt: '2026-10-12T09:30:00Z',
  autoShare: true,
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
    const instagram = () => element().querySelector('[data-testid=instagram-card]') as HTMLElement;
    return { harness, http, element, card, instagram };
  }

  it('shows the Facebook and Instagram cards "Not connected"', async () => {
    const { element, card, instagram } = await setup();

    expect(element().querySelector('h1')?.textContent).toContain('Mes réseaux');
    expect(card().dataset['state']).toBe('not-connected');
    expect(card().textContent).toContain('Non reliée');
    expect(card().textContent).toContain('Un profil personnel ne suffit pas');
    expect(instagram().dataset['state']).toBe('not-connected');
    expect(instagram().textContent).toContain('Non relié');
    expect(instagram().textContent).toContain(
      'Compte professionnel (Business ou Créateur) requis. Pas besoin de Page Facebook.',
    );
  });

  it('starts an Instagram connection in the language of the page', async () => {
    const { harness, http, instagram } = await setup('/ar/account/social', [], 'ar');

    (instagram().querySelector('[data-testid=instagram-connect]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/social/instagram/connect');
    expect(request.request.body).toEqual({ lang: 'ar' });
    request.flush({ authorizeUrl: 'https://www.instagram.com/oauth/authorize?state=abc' });
    await settle();
    await harness.fixture.whenStable();

    expect(go).toHaveBeenCalledWith('https://www.instagram.com/oauth/authorize?state=abc');
  });

  it('shows the connected Instagram account next to a Facebook card left "Not connected"', async () => {
    const { element, card, instagram } = await setup('/fr/account/social?instagram=connected', [
      SFAX_INSTAGRAM,
    ]);

    expect(instagram().dataset['state']).toBe('connected');
    expect(instagram().textContent).toContain('@sfax.auto');
    expect(instagram().textContent).toContain('Instagram · relié le 12 oct.');
    expect(instagram().querySelector('bdi')?.textContent).toBe('@sfax.auto');
    expect(element().querySelector('[data-testid=instagram-connected]')?.textContent).toContain(
      'Instagram relié.',
    );
    expect(card().dataset['state']).toBe('not-connected');
    expect(TestBed.inject(Router).url).toBe('/fr/account/social');
  });

  it('offers "Connect again" when Instagram refused the token', async () => {
    const { instagram } = await setup('/fr/account/social', [
      { ...SFAX_INSTAGRAM, status: 'NEEDS_RECONNECT' },
    ]);

    expect(instagram().dataset['state']).toBe('needs-reconnect');
    expect(instagram().textContent).toContain("Instagram a retiré l'autorisation");
    expect(instagram().querySelector('[data-testid=instagram-reconnect]')?.textContent).toContain(
      'Relier à nouveau',
    );
  });

  it('explains how to switch a personal Instagram account to a professional one', async () => {
    const { element } = await setup('/fr/account/social?instagram=instagram-not-professional');

    const panel = element().querySelector('[data-testid=instagram-failure]') as HTMLElement;
    expect(panel.getAttribute('role')).toBe('alert');
    expect(panel.textContent).toContain('Ce compte Instagram est personnel');
    expect(panel.querySelectorAll('[data-testid=instagram-steps] li')).toHaveLength(3);
    expect(panel.textContent).toContain('Passer à un compte professionnel');
    expect(panel.querySelector('[data-testid=instagram-retry]')?.textContent).toContain(
      'Réessayer',
    );
    expect(panel.querySelector('[data-testid=instagram-failure-dismiss]')?.textContent).toContain(
      'Plus tard',
    );
  });

  it.each([
    ['oauth-cancelled', 'Vous avez annulé la connexion'],
    ['oauth-permission-missing', "Soukly a besoin de l'autorisation de publier sur votre compte"],
    ['instagram-unavailable', "Instagram n'a pas terminé la connexion"],
  ])('explains the Instagram failure %s', async (failure, title) => {
    const { element } = await setup(`/fr/account/social?instagram=${failure}`);

    expect(element().querySelector('[data-testid=instagram-failure]')?.textContent).toContain(
      title,
    );
    expect(element().querySelector('[data-testid=facebook-failure]')).toBeNull();
  });

  it('disconnects only Instagram when both networks are connected', async () => {
    const { harness, http, card, instagram } = await setup('/fr/account/social', [
      SFAX_AUTO,
      SFAX_INSTAGRAM,
    ]);
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');

    (instagram().querySelector('[data-testid=instagram-disconnect]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    const dialog = document.querySelector('[data-testid=disconnect-dialog]') as HTMLElement;
    expect(dialog.textContent).toContain('Déconnecter ce compte Instagram ?');
    expect(dialog.textContent).toContain('@sfax.auto');
    (dialog.querySelector('[data-testid=disconnect-confirm]') as HTMLButtonElement).click();
    await settle();
    const request = http.expectOne('/api/social/instagram');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    await harness.fixture.whenStable();

    expect(instagram().dataset['state']).toBe('not-connected');
    expect(card().dataset['state']).toBe('connected');
    expect(show).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'Compte Instagram déconnecté.' }),
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

  describe('tick by default for every new listing (SOC-03)', () => {
    afterEach(() => sessionStorage.clear());

    it('is a switch under each connected network, saved at once', async () => {
      const { harness, http, card, instagram } = await setup('/fr/account/social', [
        SFAX_AUTO,
        SFAX_INSTAGRAM,
      ]);
      const toggle = card().querySelector(
        '[data-testid=facebook-auto-share] button',
      ) as HTMLElement;
      expect(toggle.getAttribute('role')).toBe('switch');
      expect(toggle.getAttribute('aria-checked')).toBe('true');
      expect(card().textContent).toContain('Cocher par défaut pour chaque nouvelle annonce');
      expect(instagram().querySelector('[data-testid=instagram-auto-share]')).not.toBeNull();

      toggle.click();
      await harness.fixture.whenStable();
      expect(toggle.getAttribute('aria-checked')).toBe('false');
      const request = http.expectOne('/api/social/connections/facebook');
      expect(request.request.method).toBe('PATCH');
      expect(request.request.body).toEqual({ autoShare: false });
      request.flush({ ...SFAX_AUTO, autoShare: false });
      await settle();
      await harness.fixture.whenStable();

      expect(toggle.getAttribute('aria-checked')).toBe('false');
      http.verify();
    });

    it('goes back when the saving fails', async () => {
      const { harness, http, element, instagram } = await setup('/fr/account/social', [
        SFAX_INSTAGRAM,
      ]);
      const toggle = instagram().querySelector(
        '[data-testid=instagram-auto-share] button',
      ) as HTMLElement;

      toggle.click();
      http
        .expectOne('/api/social/connections/instagram')
        .flush({}, { status: 500, statusText: 'Error' });
      await settle();
      await harness.fixture.whenStable();

      expect(toggle.getAttribute('aria-checked')).toBe('true');
      expect(element().querySelector('[data-testid=auto-share-error]')?.textContent).toContain(
        "Votre préférence n'a pas été enregistrée",
      );
    });

    it('is not offered for a network to connect again', async () => {
      const { card } = await setup('/fr/account/social', [
        { ...SFAX_AUTO, status: 'NEEDS_RECONNECT' },
      ]);

      expect(card().querySelector('[data-testid=facebook-auto-share]')).toBeNull();
    });

    it('offers to go back to the preview of the draft that sent the member here', async () => {
      const preview = '/fr/publish/0b9d6c3e-6a43-4c5e-9f0e-2d7a1b8c4e51/preview';
      const { element } = await setup(
        `/fr/account/social?returnTo=${encodeURIComponent(preview)}`,
        [],
      );

      const back = element().querySelector('[data-testid=social-return]') as HTMLAnchorElement;
      expect(back.getAttribute('href')).toBe(preview);
      expect(back.textContent).toContain('Revenir à mon annonce');
      expect(TestBed.inject(Router).url).toBe('/fr/account/social');
    });

    it('never goes back to another site', async () => {
      const { element } = await setup('/fr/account/social?returnTo=https%3A%2F%2Fevil.example', []);

      expect(element().querySelector('[data-testid=social-return]')).toBeNull();
    });
  });

  describe('relaunch after connecting again (SOC-06)', () => {
    const titles = [
      'Canapé d’angle en velours',
      'Table basse en olivier',
      'Lampe berbère en cuivre',
    ];
    const blocked = (count: number) => ({
      network: 'FACEBOOK',
      total: count,
      listings: Array.from({ length: count }, (_, i) => ({
        listingId: `l-${i}`,
        title: titles[i] ?? `Annonce ${i + 1}`,
      })),
    });

    async function connectedAgain(count: number) {
      const page = await setup('/fr/account/social?facebook=connected', [SFAX_AUTO]);
      const read = page.http.expectOne((request) => request.url === '/api/social/posts/blocked');
      expect(read.request.params.get('network')).toBe('FACEBOOK');
      read.flush(blocked(count));
      await settle();
      await page.harness.fixture.whenStable();
      const banner = () =>
        page.element().querySelector('[data-testid=relaunch-banner]') as HTMLElement | null;
      return { ...page, banner };
    }

    it('offers to post the listings blocked meanwhile, then posts them', async () => {
      const { harness, http, element, banner } = await connectedAgain(3);
      const show = vi.spyOn(TestBed.inject(ToastService), 'show').mockReturnValue(1);

      expect(banner()?.getAttribute('role')).toBe('status');
      expect(banner()?.textContent).toContain(
        'Page reliée à nouveau. 3 annonces n’ont pas été publiées sur Facebook pendant la coupure. Les publier maintenant ?',
      );
      const items = [...(banner()?.querySelectorAll('li') ?? [])].map((li) =>
        li.textContent?.trim(),
      );
      expect(items).toEqual(titles);
      expect(element().querySelector('[data-testid=facebook-connected]')).toBeNull();

      const confirm = banner()?.querySelector('[data-testid=relaunch-confirm]') as HTMLElement;
      expect(confirm.textContent?.trim()).toBe('Publier les 3 annonces');
      confirm.click();
      const relaunch = http.expectOne('/api/social/posts/relaunch');
      expect(relaunch.request.body).toEqual({ network: 'FACEBOOK' });
      relaunch.flush({ relaunched: 3 });
      await settle();
      await harness.fixture.whenStable();

      expect(banner()).toBeNull();
      expect(show.mock.calls.at(-1)?.[0].message).toBe(
        'Les annonces vont être publiées sur Facebook dans les prochaines minutes.',
      );
    });

    it('lists 5 titles at most, and "No thanks" changes nothing', async () => {
      const { harness, http, banner } = await connectedAgain(7);

      const items = [...(banner()?.querySelectorAll('li') ?? [])].map((li) =>
        li.textContent?.trim(),
      );
      expect(items).toHaveLength(6);
      expect(items.at(-1)).toBe('et 2 autres');

      (banner()?.querySelector('[data-testid=relaunch-dismiss]') as HTMLElement).click();
      await harness.fixture.whenStable();
      expect(banner()).toBeNull();
      http.expectNone('/api/social/posts/relaunch');
    });

    it('shows no banner after a first connection', async () => {
      const { element, banner } = await connectedAgain(0);

      expect(banner()).toBeNull();
      expect(element().querySelector('[data-testid=facebook-connected]')).not.toBeNull();
    });

    it('is in Arabic', async () => {
      const page = await setup('/ar/account/social?instagram=connected', [SFAX_INSTAGRAM], 'ar');
      page.http
        .expectOne((request) => request.url === '/api/social/posts/blocked')
        .flush({ ...blocked(1), network: 'INSTAGRAM' });
      await settle();
      await page.harness.fixture.whenStable();

      const banner = page.element().querySelector('[data-testid=relaunch-banner]');
      expect(banner?.textContent).toContain('تم ربط الحساب من جديد.');
      expect(banner?.textContent).toContain('لم يُنشر إعلان واحد على إنستغرام أثناء الانقطاع.');
      expect(banner?.querySelector('[data-testid=relaunch-confirm]')?.textContent?.trim()).toBe(
        'نشر الإعلان',
      );
    });
  });
});
