import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LocaleService } from '../locale.service';
import { LocalDatePipe } from './local-date.pipe';
import { PricePipe } from './price.pipe';
import { RelativeTimePipe } from './relative-time.pipe';

@Component({
  imports: [PricePipe, LocalDatePipe, RelativeTimePipe],
  template: `
    <bdi data-price>{{ price | price }}</bdi>
    <span data-date>{{ date | localDate }}</span>
    <span data-relative>{{ yesterday | relativeTime }}</span>
    <span data-empty
      >{{ missing | price }}{{ missing | localDate }}{{ missing | relativeTime }}</span
    >
  `,
})
class Host {
  price = 1250;
  date = new Date(2026, 9, 12);
  yesterday = Date.now() - 24 * 3600_000;
  missing: number | null = null;
}

function text(element: HTMLElement, selector: string): string {
  return (element.querySelector(selector)?.textContent ?? '')
    .replace(/[\u200e\u200f]/g, '')
    .replace(/[\u00a0\u202f]/g, ' ');
}

describe('format pipes', () => {
  afterEach(() => TestBed.inject(LocaleService).use('fr'));

  it('format with the locale of the current language and follow its changes', async () => {
    const fixture = TestBed.createComponent(Host);
    const element = fixture.nativeElement as HTMLElement;
    const locale = TestBed.inject(LocaleService);
    await fixture.whenStable();

    expect(text(element, '[data-price]')).toBe('1 250 DT');
    expect(text(element, '[data-date]')).toBe('12 oct. 2026');
    expect(text(element, '[data-relative]')).toBe('hier');

    locale.use('ar');
    await fixture.whenStable();
    expect(text(element, '[data-price]')).toBe('1.250 د.ت.');
    expect(text(element, '[data-date]')).toBe('12/10/2026');
    expect(text(element, '[data-relative]')).toBe('أمس');

    locale.use('en');
    await fixture.whenStable();
    expect(text(element, '[data-price]')).toBe('TND 1,250');
    expect(text(element, '[data-date]')).toBe('Oct 12, 2026');
    expect(text(element, '[data-relative]')).toBe('yesterday');
  });

  it('render nothing for a missing value', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    expect(text(fixture.nativeElement, '[data-empty]')).toBe('');
  });
});
