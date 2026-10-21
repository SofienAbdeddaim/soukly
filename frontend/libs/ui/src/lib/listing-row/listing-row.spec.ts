import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiListingRow } from './listing-row';

@Component({
  imports: [UiListingRow],
  template: `
    <ui-listing-row
      title="Peugeot 208 diesel, première main"
      price="32 000 DT"
      [summary]="summary()"
      place="Sfax, Sakiet Ezzit"
      dateLabel="il y a 2 h"
      dateTime="2026-10-21T08:00:00Z"
      href="/fr/listings/1"
      [imageUrl]="imageUrl()"
      [photoCount]="photoCount()"
      photoCountLabel="4 photos"
    />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class Host {
  summary = signal<string | null>('2019 · 62 000 km · Diesel');
  imageUrl = signal<string | null>('cover.webp');
  photoCount = signal<number | null>(4);
}

describe('UiListingRow', () => {
  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('lays out the photo, the title link, the price, the summary and the place', async () => {
    const { element } = await render();

    expect(element.querySelector('article')).not.toBeNull();
    expect(element.querySelector('img')?.getAttribute('src')).toBe('cover.webp');
    expect(element.querySelector('img')?.getAttribute('alt')).toBe('');
    const link = element.querySelector('h3 a') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe('Peugeot 208 diesel, première main');
    expect(link.getAttribute('href')).toBe('/fr/listings/1');
    expect(element.querySelector('bdi')?.textContent).toBe('32 000 DT');
    expect(element.querySelector('.row__summary')?.textContent).toBe('2019 · 62 000 km · Diesel');
    expect(element.querySelector('time')?.getAttribute('datetime')).toBe('2026-10-21T08:00:00Z');
    expect(element.querySelector('.row__meta')?.textContent).toContain('Sfax, Sakiet Ezzit');
  });

  it('gives the number of photos to screen readers', async () => {
    const { element } = await render();

    expect(element.querySelector('.row__count .ui-visually-hidden')?.textContent).toBe('4 photos');
  });

  it('shows a placeholder without photo and no empty summary', async () => {
    const { fixture, element } = await render();
    fixture.componentInstance.imageUrl.set(null);
    fixture.componentInstance.photoCount.set(0);
    fixture.componentInstance.summary.set(null);
    await fixture.whenStable();

    expect(element.querySelector('img')).toBeNull();
    expect(element.querySelector('[data-testid="listing-row-placeholder"]')).not.toBeNull();
    expect(element.querySelector('.row__count')).toBeNull();
    expect(element.querySelector('.row__summary')).toBeNull();
  });
});
