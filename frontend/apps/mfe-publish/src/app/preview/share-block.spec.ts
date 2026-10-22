import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Lang } from '@soukly/i18n';
import { draftListing, Listing, TEST_ID } from '@soukly/listing-data';
import { SocialConnection } from '@soukly/social-data';
import {
  loadDraft,
  publishI18nTesting,
  publishTestingProviders,
  settle,
} from '../testing/publish-testing';
import { ShareBlock } from './share-block';

const LISTING = `/api/listings/${TEST_ID}`;

const DRAFT = draftListing({
  title: 'Peugeot 208 diesel, première main',
  description: 'Très bon état, entretien chez le concessionnaire, carnet à jour.',
  priceMode: 'FIXED',
  price: 32000,
  negotiable: true,
  governorateCode: 'sfax',
  delegationCode: 'sfax.sakiet-ezzit',
  photos: [
    {
      id: 'a',
      url: 'https://photos.test/a.webp',
      thumbnailUrl: 'https://photos.test/a-400.webp',
      width: 1600,
      height: 1200,
      position: 0,
    },
  ],
  missingFields: [],
});

function connection(overrides: Partial<SocialConnection>): SocialConnection {
  return {
    network: 'FACEBOOK',
    status: 'CONNECTED',
    displayName: 'Sfax Auto Occasion',
    pictureUrl: null,
    connectedAt: '2026-10-12T09:30:00Z',
    autoShare: true,
    ...overrides,
  };
}

const FACEBOOK = connection({});
const INSTAGRAM_OFF = connection({
  network: 'INSTAGRAM',
  displayName: '@sfax.auto',
  autoShare: false,
});

describe('ShareBlock', () => {
  let http: HttpTestingController;

  async function setup(listing: Listing, connections: SocialConnection[], lang: Lang = 'fr') {
    TestBed.configureTestingModule({
      imports: [publishI18nTesting(lang)],
      providers: publishTestingProviders(),
    });
    http = TestBed.inject(HttpTestingController);
    const store = await loadDraft(listing, lang);
    const fixture = TestBed.createComponent(ShareBlock);
    await fixture.whenStable();
    http.expectOne('/api/social/connections').flush(connections);
    await settle();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const render = async () => {
      await settle();
      await fixture.whenStable();
    };
    const box = (network: string) =>
      element.querySelector(`[data-testid=share-${network}] input`) as HTMLInputElement | null;
    return { fixture, element, render, box, store };
  }

  /** Answers the autosave of the draft with the patch applied. */
  function answerSave(listing: Listing, version: number) {
    const request = http.expectOne(
      (candidate) => candidate.method === 'PATCH' && candidate.url === LISTING,
    );
    const body = request.request.body as Partial<Listing>;
    request.flush({ ...listing, ...body, version }, { headers: { ETag: `"${version}"` } });
    return body;
  }

  afterEach(() => http.verify());

  it('ticks the networks of the preference on the first preview and saves them', async () => {
    const { box, render, store } = await setup(DRAFT, [FACEBOOK, INSTAGRAM_OFF]);

    const body = answerSave(DRAFT, 1);
    await render();

    expect(body).toEqual({ shareTo: ['FACEBOOK'], shareLanguage: 'fr' });
    expect(box('FACEBOOK')?.checked).toBe(true);
    expect(box('INSTAGRAM')?.checked).toBe(false);
    expect(store.listing()?.shareTo).toEqual(['FACEBOOK']);
  });

  it('shows the saved choice afterwards, without saving it again', async () => {
    const { box } = await setup({ ...DRAFT, shareTo: ['FACEBOOK', 'INSTAGRAM'] }, [
      FACEBOOK,
      INSTAGRAM_OFF,
    ]);

    expect(box('FACEBOOK')?.checked).toBe(true);
    expect(box('INSTAGRAM')?.checked).toBe(true);
  });

  it('saves each change with the language of the page', async () => {
    const { box, render } = await setup(
      { ...DRAFT, shareTo: ['FACEBOOK'] },
      [FACEBOOK, INSTAGRAM_OFF],
      'ar',
    );

    box('INSTAGRAM')?.click();
    await render();

    expect(answerSave(DRAFT, 1)).toEqual({
      shareTo: ['FACEBOOK', 'INSTAGRAM'],
      shareLanguage: 'ar',
    });
  });

  it('cannot tick a network to connect again, and links to My networks', async () => {
    const { box, element } = await setup({ ...DRAFT, shareTo: ['FACEBOOK', 'INSTAGRAM'] }, [
      FACEBOOK,
      { ...INSTAGRAM_OFF, status: 'NEEDS_RECONNECT' },
    ]);

    expect(box('INSTAGRAM')?.checked).toBe(false);
    expect(box('INSTAGRAM')?.disabled).toBe(true);
    expect(element.textContent).toContain('à relier à nouveau');
    const link = element.querySelector(
      '[data-testid=share-reconnect-INSTAGRAM]',
    ) as HTMLAnchorElement;
    expect(link.textContent).toContain('Relier à nouveau Instagram');
    expect(link.getAttribute('href')).toMatch(/^\/fr\/account\/social\?returnTo=/);
  });

  it('asks to connect a network when none is', async () => {
    const { element } = await setup(DRAFT, []);

    expect(element.querySelector('[data-testid=share-none]')?.textContent).toContain(
      'Reliez Facebook ou Instagram pour partager vos annonces',
    );
    expect(element.querySelector('input')).toBeNull();
  });

  it('reads the post text when it is opened, and offers to try again on an error', async () => {
    const { element, render } = await setup({ ...DRAFT, shareTo: ['FACEBOOK'] }, [FACEBOOK]);
    const details = element.querySelector('details') as HTMLDetailsElement;

    details.open = true;
    details.dispatchEvent(new Event('toggle'));
    await render();
    const first = http.expectOne('/api/social/caption-preview');
    expect(first.request.body).toEqual(
      expect.objectContaining({
        title: 'Peugeot 208 diesel, première main',
        priceMode: 'FIXED',
        priceTnd: 32000,
        negotiable: true,
        categoryCode: 'vehicles.cars',
        governorateCode: 'sfax',
        delegationCode: 'sfax.sakiet-ezzit',
        language: 'fr',
        publicUrl: expect.stringMatching(new RegExp(`/fr/listings/${TEST_ID}$`)),
      }),
    );
    first.flush({}, { status: 503, statusText: 'Unavailable' });
    await render();
    expect(element.querySelector('[data-testid=share-caption-error]')?.textContent).toContain(
      'Texte indisponible',
    );

    (
      element.querySelector('[data-testid=share-caption-error] button') as HTMLButtonElement
    ).click();
    await render();
    http.expectOne('/api/social/caption-preview').flush({
      text: 'Peugeot 208 diesel, première main\n32 000 DT · Négociable\n#Soukly #Sfax #Voitures',
      hashtags: ['#Soukly', '#Sfax', '#Voitures'],
    });
    await render();

    expect(element.querySelector('[data-testid=share-caption-text]')?.textContent).toContain(
      '#Soukly #Sfax #Voitures',
    );
    expect(element.querySelector('.share__thumbnail')?.getAttribute('src')).toBe(
      'https://photos.test/a-400.webp',
    );
    expect(element.textContent).toContain('Avec votre photo');
  });

  it('keeps a draft of a member without network unchanged', async () => {
    const { store } = await setup(DRAFT, []);

    expect(store.listing()?.shareTo).toBeNull();
    expect(store.saveState()).toBe('idle');
  });
});
