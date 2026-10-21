import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { Lang, LocaleService } from '@soukly/i18n';
import {
  draftListing,
  OWN_COUNTS,
  ownListingCard,
  OwnListingCard,
  OwnListingStatus,
  TEST_ID,
} from '@soukly/listing-data';
import { ToastService } from '@soukly/ui';
import { BehaviorSubject } from 'rxjs';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { MyListingsPage } from './my-listings-page';

const NOW = Date.parse('2026-10-21T10:00:00Z');
const DAY = 24 * 60 * 60 * 1000;
const inDays = (days: number) => new Date(NOW + days * DAY).toISOString();
const settle = () => new Promise((resolve) => setTimeout(resolve));

const ONLINE = ownListingCard({ expiresAt: inDays(58) });
const SOON = ownListingCard({
  id: 'b1c2d3e4-0000-4000-8000-000000000002',
  title: 'Canapé d’angle en cuir',
  price: 1200,
  expiresAt: inDays(1.5),
  contactRevealsCount: 1,
});

describe('MyListingsPage', () => {
  let http: HttpTestingController;
  let query: BehaviorSubject<ParamMap>;

  async function setup(tab: string | null = null, lang: Lang = 'fr') {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(NOW);
    query = new BehaviorSubject(convertToParamMap(tab ? { tab } : {}));
    TestBed.configureTestingModule({
      imports: [accountI18nTesting(lang)],
      providers: [
        ...accountTestingProviders(),
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: query, snapshot: { queryParamMap: query.value } },
        },
      ],
    });
    TestBed.inject(LocaleService).use(lang);
    http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const show = vi.spyOn(TestBed.inject(ToastService), 'show').mockReturnValue(1);
    const fixture = TestBed.createComponent(MyListingsPage);
    const element = fixture.nativeElement as HTMLElement;
    const render = async () => {
      await settle();
      await fixture.whenStable();
    };
    await fixture.whenStable();
    const byTestId = (id: string) => element.querySelector(`[data-testid=${id}]`) as HTMLElement;
    const all = (id: string) => [...element.querySelectorAll<HTMLElement>(`[data-testid=${id}]`)];
    const text = (node: Element | null) => node?.textContent?.replace(/\s+/g, ' ').trim();
    return { fixture, element, render, byTestId, all, text, navigate, show };
  }

  function expectMine(status: OwnListingStatus): TestRequest {
    return http.expectOne(
      (request) => request.url === '/api/listings/mine' && request.params.get('status') === status,
    );
  }

  function flushMine(
    status: OwnListingStatus,
    items: OwnListingCard[],
    counts: Partial<Record<OwnListingStatus, number>> = OWN_COUNTS,
  ) {
    expectMine(status).flush({ items, counts });
  }

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
    document.querySelectorAll('.cdk-overlay-container').forEach((node) => node.remove());
  });

  it('shows the tabs with their counts and the listings online', async () => {
    const { render, all, text, byTestId } = await setup();
    expect(byTestId('listings-panel').getAttribute('aria-busy')).toBe('true');
    flushMine('PUBLISHED', [ONLINE, SOON]);
    await render();

    expect(all('tab-online')[0].getAttribute('aria-selected')).toBe('true');
    expect(text(byTestId('tab-online'))).toBe('En ligne 3');
    expect(text(byTestId('tab-drafts'))).toBe('Brouillons 1');
    expect(text(byTestId('tab-expired'))).toBe('Expirées 1');
    expect(text(byTestId('tab-inactive'))).toBe('Désactivées');

    const [first, second] = all('own-listing');
    expect(text(first.querySelector('[data-testid=own-listing-title]'))).toBe(ONLINE.title);
    expect(text(first.querySelector('[data-testid=own-listing-price]'))).toMatch(/32\s000\sDT/);
    expect(text(first.querySelector('[data-testid=own-listing-badge]'))).toBe('En ligne · 58 j');
    expect(text(first.querySelector('[data-testid=own-listing-reveals]'))).toBe(
      '8 numéros affichés',
    );
    expect(text(first.querySelector('[data-testid=own-listing-primary]'))).toBe('Modifier');
    expect(text(second.querySelector('[data-testid=own-listing-badge]'))).toBe('Expire dans 2 j');
    expect(text(second.querySelector('[data-testid=own-listing-primary]'))).toBe(
      'Prolonger de 60 jours',
    );
  });

  it('opens the tab of the URL, with the note of the expired listings', async () => {
    const { render, byTestId, text } = await setup('expired');
    flushMine('EXPIRED', [ownListingCard({ status: 'EXPIRED', expiresAt: inDays(-11) })]);
    await render();

    expect(byTestId('tab-expired').getAttribute('aria-selected')).toBe('true');
    expect(text(byTestId('own-listing-badge'))).toMatch(/^Expirée le 10 oct\.? 2026$/);
    expect(text(byTestId('listings-panel'))).toContain(
      'Une annonce remise en ligne reste visible 60 jours de plus.',
    );
    expect(text(byTestId('own-listing-primary'))).toBe('Remettre en ligne');
  });

  it('puts the chosen tab in the URL, also with the arrow keys', async () => {
    const { render, byTestId, navigate } = await setup();
    flushMine('PUBLISHED', [ONLINE]);
    await render();

    byTestId('tab-drafts').click();
    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { tab: 'drafts' } }),
    );

    byTestId('tab-online').dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }));
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({ queryParams: { tab: 'inactive' } }),
    );

    query.next(convertToParamMap({ tab: 'drafts' }));
    await render();
    flushMine('DRAFT', [
      ownListingCard({ status: 'DRAFT', expiresAt: null, missingFieldsCount: 3 }),
    ]);
    await render();
    expect(byTestId('own-listing-badge').textContent?.trim()).toBe(
      'Brouillon · 3 champs manquants',
    );
    expect(byTestId('own-listing-primary').getAttribute('href')).toBe(`/fr/publish/${TEST_ID}`);
  });

  it('deactivates without confirmation, with "Undo" in the toast', async () => {
    const { render, byTestId, all, show } = await setup();
    flushMine('PUBLISHED', [ONLINE, SOON]);
    await render();

    byTestId('own-listing-menu').click();
    await render();
    const items = [...document.querySelectorAll<HTMLElement>('[role=menuitem]')].map((item) =>
      item.textContent?.trim(),
    );
    expect(items).toEqual(["Voir l'annonce", 'Partager le lien', 'Désactiver', 'Supprimer']);
    (document.querySelector('[data-testid=menu-deactivate]') as HTMLElement).click();
    await render();

    const request = http.expectOne(`/api/listings/${TEST_ID}/deactivate`);
    expect(request.request.method).toBe('POST');
    request.flush(draftListing({ status: 'INACTIVE' }), { headers: { ETag: '"5"' } });
    await render();

    expect(all('own-listing')).toHaveLength(1);
    flushMine('PUBLISHED', [SOON], { ...OWN_COUNTS, PUBLISHED: 2, INACTIVE: 1 });
    await render();
    expect(byTestId('tab-inactive').textContent).toContain('1');

    const toast = show.mock.calls.at(-1)?.[0];
    expect(toast).toEqual(
      expect.objectContaining({ message: 'Annonce désactivée', duration: 5000 }),
    );
    expect(toast?.action?.label).toBe('Annuler');

    toast?.action?.run();
    await render();
    http
      .expectOne(`/api/listings/${TEST_ID}/renew`)
      .flush(draftListing({ status: 'PUBLISHED' }), { headers: { ETag: '"6"' } });
    await render();
    flushMine('PUBLISHED', [ONLINE, SOON]);
    await render();
    expect(show.mock.calls.at(-1)?.[0].message).toBe('Annonce remise en ligne');
  });

  it('asks before deleting, then deletes', async () => {
    const { render, byTestId, all, show } = await setup();
    flushMine('PUBLISHED', [ONLINE]);
    await render();

    byTestId('own-listing-menu').click();
    await render();
    (document.querySelector('[data-testid=menu-delete]') as HTMLElement).click();
    await render();

    const dialog = document.querySelector('[data-testid=delete-dialog]') as HTMLElement;
    expect(dialog.closest('[role=alertdialog]')).not.toBeNull();
    expect(dialog.textContent).toContain('Supprimer cette annonce ?');
    expect(dialog.textContent).toContain(ONLINE.title as string);
    expect(dialog.textContent).toContain('Cette action est définitive.');
    expect(dialog.querySelector('[data-testid=delete-deactivate]')).not.toBeNull();

    (dialog.querySelector('[data-testid=delete-confirm]') as HTMLElement).click();
    await render();
    const request = http.expectOne(`/api/listings/${TEST_ID}`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await render();

    expect(all('own-listing')).toHaveLength(0);
    flushMine('PUBLISHED', [], { ...OWN_COUNTS, PUBLISHED: 2 });
    await render();
    expect(show.mock.calls.at(-1)?.[0].message).toBe('Annonce supprimée');
  });

  it('extends a listing that expires soon', async () => {
    const { render, byTestId, show } = await setup();
    flushMine('PUBLISHED', [SOON]);
    await render();

    byTestId('own-listing-primary').click();
    await render();
    http
      .expectOne(`/api/listings/${SOON.id}/renew`)
      .flush(draftListing({ status: 'PUBLISHED' }), { headers: { ETag: '"6"' } });
    await render();
    flushMine('PUBLISHED', [{ ...SOON, expiresAt: inDays(60) }]);
    await render();

    expect(show.mock.calls.at(-1)?.[0].message).toBe('Annonce en ligne pour 60 jours');
    expect(byTestId('own-listing-badge').textContent?.trim()).toBe('En ligne · 60 j');
  });

  it('shows an empty tab with a link to post a listing', async () => {
    const { render, byTestId, text } = await setup('inactive');
    flushMine('INACTIVE', []);
    await render();

    expect(text(byTestId('listings-empty'))).toBe('Aucune annonce désactivée Déposer une annonce');
  });

  it('shows an error with "Try again"', async () => {
    const { render, byTestId, element } = await setup();
    expectMine('PUBLISHED').flush(null, { status: 503, statusText: 'Service Unavailable' });
    await render();

    expect(byTestId('listings-error').textContent).toContain(
      "Vos annonces n'ont pas pu être chargées.",
    );
    const retry = [...element.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === 'Réessayer',
    ) as HTMLButtonElement;
    retry.click();
    await render();
    flushMine('PUBLISHED', [ONLINE]);
    await render();
    expect(byTestId('own-listing')).not.toBeNull();
  });

  it('loads the next page with "See more"', async () => {
    const { render, byTestId, all } = await setup();
    expectMine('PUBLISHED').flush({ items: [ONLINE], counts: OWN_COUNTS, nextCursor: 'abc' });
    await render();

    byTestId('listings-more').click();
    await render();
    http
      .expectOne(
        (request) => request.url === '/api/listings/mine' && request.params.get('cursor') === 'abc',
      )
      .flush({ items: [SOON] });
    await render();

    expect(all('own-listing')).toHaveLength(2);
    expect(byTestId('listings-more')).toBeNull();
  });

  it('is in Arabic with ar-TN prices', async () => {
    const { render, byTestId, text, element } = await setup(null, 'ar');
    flushMine('PUBLISHED', [ONLINE]);
    await render();

    expect(text(element.querySelector('h1'))).toBe('إعلاناتي');
    expect(text(byTestId('tab-online'))).toBe('منشورة 3');
    expect(text(byTestId('own-listing-primary'))).toBe('تعديل');
    expect(text(byTestId('own-listing-price'))).toContain('د.ت.');
  });
});
