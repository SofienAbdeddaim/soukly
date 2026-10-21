import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiField, UiFieldControl, UiFieldPrefix, UiFieldSuffix } from './field';

@Component({
  imports: [UiField, UiFieldControl],
  template: `
    <ui-field label="Prix" [hint]="hint()" [error]="error()" [required]="required()">
      <input uiControl type="text" />
    </ui-field>
    <ui-field label="Catégorie" controlId="category">
      <select uiControl>
        <option>Services</option>
      </select>
    </ui-field>
  `,
})
class Host {
  hint = signal<string | undefined>('Montant en dinars');
  error = signal<string | null>(null);
  required = signal(false);
}

describe('UiField', () => {
  async function setup() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      host: fixture.componentInstance,
      element,
      label: element.querySelector('label') as HTMLLabelElement,
      input: element.querySelector('input') as HTMLInputElement,
    };
  }

  it('links the label to the control and the control to its hint', async () => {
    const { element, label, input } = await setup();
    expect(input.id).toMatch(/^ui-field-\d+$/);
    expect(label.htmlFor).toBe(input.id);
    expect(label.textContent).toContain('Prix');
    const hint = element.querySelector(`#${input.id}-hint`);
    expect(hint?.textContent).toContain('Montant en dinars');
    expect(input.getAttribute('aria-describedby')).toBe(`${input.id}-hint`);
    expect(input.getAttribute('aria-invalid')).toBeNull();
  });

  it('sets aria-invalid and points aria-describedby to the error message', async () => {
    const { fixture, host, element, input } = await setup();
    host.error.set('Indiquez un prix en dinars, sans millimes.');
    await fixture.whenStable();
    expect(input.getAttribute('aria-invalid')).toBe('true');
    const describedBy = input.getAttribute('aria-describedby')?.split(' ') ?? [];
    expect(describedBy).toContain(`${input.id}-error`);
    const error = element.querySelector(`#${input.id}-error`);
    expect(error?.textContent).toContain('Indiquez un prix en dinars, sans millimes.');
    expect(element.querySelector('ui-field')?.classList).toContain('ui-field--invalid');
  });

  it('has no aria-describedby without hint or error', async () => {
    const { fixture, host, input } = await setup();
    host.hint.set(undefined);
    await fixture.whenStable();
    expect(input.hasAttribute('aria-describedby')).toBe(false);
  });

  it('marks the control required and shows a decorative marker', async () => {
    const { fixture, host, label, input } = await setup();
    host.required.set(true);
    await fixture.whenStable();
    expect(input.required).toBe(true);
    expect(label.querySelector('[aria-hidden=true]')?.textContent).toBe('*');
  });

  it('uses the given control id for a select', async () => {
    const { element } = await setup();
    const select = element.querySelector('select') as HTMLSelectElement;
    expect(select.id).toBe('category');
    expect(element.querySelector('label[for=category]')?.textContent).toContain('Catégorie');
  });

  it('projects a prefix before the control and a suffix button after it, left-to-right on demand', async () => {
    const fixture = TestBed.createComponent(AddonsHost);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const group = element.querySelector('.ui-field__group') as HTMLElement;
    const children = Array.from(group.children);

    expect(group.getAttribute('dir')).toBe('ltr');
    expect(children.map((child) => child.tagName)).toEqual(['SPAN', 'INPUT', 'BUTTON']);
    expect(children[0].classList).toContain('ui-field__prefix');
    expect(children[2].classList).toContain('ui-field__suffix');
  });
});

@Component({
  imports: [UiField, UiFieldControl, UiFieldPrefix, UiFieldSuffix],
  template: `
    <ui-field label="Téléphone" ltr>
      <button uiFieldSuffix type="button" aria-label="Effacer">x</button>
      <input uiControl type="tel" />
      <span uiFieldPrefix>+216</span>
    </ui-field>
  `,
})
class AddonsHost {}
