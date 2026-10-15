import { TestBed } from '@angular/core/testing';
import axe from 'axe-core';
import { App } from './app';

/**
 * Automated accessibility check of the showcase page with axe-core, in LTR and in RTL.
 * jsdom has no layout: colour contrast is measured in a real browser (see libs/ui/README.md).
 */
async function seriousViolations(): Promise<string[]> {
  const results = await axe.run(document.body, {
    runOnly: {
      type: 'tag',
      values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'],
    },
  });
  return results.violations
    .filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
    .map(
      (violation) =>
        `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`,
    );
}

describe('App (ui-showcase)', () => {
  afterEach(() => {
    document.documentElement.lang = 'fr';
    document.documentElement.dir = 'ltr';
  });

  async function setup() {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('shows every component: buttons, fields, listing cards, skeletons and toast triggers', async () => {
    const { element } = await setup();
    expect(element.querySelector('h1')?.textContent).toContain('Soukly');
    expect(element.querySelectorAll('[ui-button]').length).toBeGreaterThan(8);
    expect(element.querySelectorAll('ui-field').length).toBe(4);
    expect(element.querySelectorAll('ui-listing-card').length).toBe(3);
    expect(element.querySelectorAll('ui-skeleton').length).toBeGreaterThan(1);
    expect(element.querySelector('[aria-invalid=true]')).not.toBeNull();
    expect(element.querySelector('[aria-busy=true]')).not.toBeNull();
  });

  it('switches <html> between ltr/fr and rtl/ar', async () => {
    const { fixture, element } = await setup();
    const toggle = element.querySelector('[data-testid=toggle-direction]') as HTMLButtonElement;

    toggle.click();
    await fixture.whenStable();
    expect(document.documentElement.dir).toBe('rtl');
    expect(document.documentElement.lang).toBe('ar');
    expect(element.querySelector('h1')?.textContent).toContain('سوقلي');

    toggle.click();
    await fixture.whenStable();
    expect(document.documentElement.dir).toBe('ltr');
    expect(document.documentElement.lang).toBe('fr');
  });

  it('formats the card prices and dates with the locale of the language (@soukly/i18n pipes)', async () => {
    const { fixture, element } = await setup();
    const prices = () =>
      Array.from(element.querySelectorAll('.card__price')).map((price) =>
        (price.textContent ?? '').replace(/[\u200e\u200f]/g, '').replace(/[\u00a0\u202f]/g, ' '),
      );
    const dates = () => Array.from(element.querySelectorAll('time')).map((t) => t.textContent);

    expect(prices()).toEqual(['180 DT', '25 DT / h', '1 250 DT / mois']);
    expect(dates()[1]).toBe('hier');

    (element.querySelector('[data-testid=toggle-direction]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(prices()).toEqual(['180 د.ت.', '25 د.ت. / ساعة', '1.250 د.ت. / شهر']);
    expect(dates()[1]).toBe('أمس');
  });

  it('has no serious or critical axe violation in LTR and in RTL', async () => {
    const { fixture, element } = await setup();
    expect(await seriousViolations()).toEqual([]);

    (element.querySelector('[data-testid=toggle-direction]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(await seriousViolations()).toEqual([]);
  });
});
