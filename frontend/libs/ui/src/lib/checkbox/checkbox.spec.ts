import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiCheckbox, UiCheckboxControl } from './checkbox';

@Component({
  imports: [UiCheckbox, UiCheckboxControl],
  template: `
    <ui-checkbox [error]="error()">
      <input uiCheckboxControl type="checkbox" />
      J'accepte les <a href="/fr/terms">conditions d'utilisation</a>
    </ui-checkbox>
  `,
})
class Host {
  error = signal<string | null>(null);
}

describe('UiCheckbox', () => {
  async function setup() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      host: fixture.componentInstance,
      element,
      input: element.querySelector('input') as HTMLInputElement,
    };
  }

  it('links the label, links included, to the checkbox', async () => {
    const { element, input } = await setup();
    const label = element.querySelector('label') as HTMLLabelElement;
    expect(input.id).toMatch(/^ui-checkbox-\d+$/);
    expect(label.htmlFor).toBe(input.id);
    expect(label.querySelector('a')?.getAttribute('href')).toBe('/fr/terms');
    expect(input.getAttribute('aria-invalid')).toBeNull();
  });

  it('describes the checkbox by its error', async () => {
    const { fixture, host, element, input } = await setup();
    host.error.set('Vous devez accepter les conditions.');
    await fixture.whenStable();
    expect(input.getAttribute('aria-invalid')).toBe('true');
    const error = element.querySelector(`#${input.getAttribute('aria-describedby')}`);
    expect(error?.textContent).toContain('Vous devez accepter les conditions.');
  });
});
