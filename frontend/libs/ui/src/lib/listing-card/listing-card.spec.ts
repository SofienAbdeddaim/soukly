import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiListingCard } from './listing-card';

@Component({
  imports: [UiListingCard],
  template: `
    <ui-listing-card
      title="Vélo de ville"
      price="1 250 DT"
      city="Sousse"
      dateLabel="il y a 2 h"
      dateTime="2026-10-15T10:00:00Z"
      typeLabel="Vente"
      href="/fr/annonces/42"
      [imageUrl]="imageUrl()"
      [favorite]="favorite()"
      favoriteLabel="Ajouter aux favoris"
      (favoriteToggle)="toggled = $event"
    />
  `,
})
class Host {
  imageUrl = signal<string | null>(null);
  favorite = signal(false);
  toggled: boolean | undefined;
}

describe('UiListingCard', () => {
  async function setup() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return {
      fixture,
      host: fixture.componentInstance,
      element: fixture.nativeElement as HTMLElement,
    };
  }

  it('renders an article with an h3 title link, the price, the city and the date', async () => {
    const { element } = await setup();
    const article = element.querySelector('article');
    expect(article).not.toBeNull();
    const link = article?.querySelector('h3 a') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe('Vélo de ville');
    expect(link.getAttribute('href')).toBe('/fr/annonces/42');
    expect(article?.textContent).toContain('Sousse');
    expect(article?.querySelector('time')?.getAttribute('datetime')).toBe('2026-10-15T10:00:00Z');
    expect(article?.textContent).toContain('Vente');
  });

  it('renders the price left-to-right in an isolated element', async () => {
    const { element } = await setup();
    const price = element.querySelector('.card__price bdi');
    expect(price?.getAttribute('dir')).toBe('ltr');
    expect(price?.textContent).toBe('1 250 DT');
  });

  it('shows a placeholder without image and the image when there is one', async () => {
    const { fixture, host, element } = await setup();
    expect(element.querySelector('[data-testid=listing-card-placeholder]')).not.toBeNull();
    expect(element.querySelector('img')).toBeNull();

    host.imageUrl.set('/photos/42.webp');
    await fixture.whenStable();
    const img = element.querySelector('img') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe('/photos/42.webp');
    expect(img.getAttribute('alt')).toBe('');
    expect(element.querySelector('[data-testid=listing-card-placeholder]')).toBeNull();
  });

  it('exposes the favourite state with aria-pressed and emits the next state', async () => {
    const { fixture, host, element } = await setup();
    const button = element.querySelector('.card__favorite') as HTMLButtonElement;
    expect(button.getAttribute('aria-label')).toBe('Ajouter aux favoris');
    expect(button.getAttribute('aria-pressed')).toBe('false');
    button.click();
    expect(host.toggled).toBe(true);

    host.favorite.set(true);
    await fixture.whenStable();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    button.click();
    expect(host.toggled).toBe(false);
  });
});
