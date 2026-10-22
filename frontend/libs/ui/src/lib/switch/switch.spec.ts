import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiSwitch } from './switch';

@Component({
  imports: [UiSwitch],
  template: `<ui-switch [(checked)]="checked" [disabled]="disabled()"
    >Cocher par défaut</ui-switch
  >`,
})
class Host {
  checked = signal(true);
  disabled = signal(false);
}

describe('UiSwitch', () => {
  async function setup() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      host: fixture.componentInstance,
      element,
      button: element.querySelector('button') as HTMLButtonElement,
    };
  }

  it('is a switch named by its label', async () => {
    const { element, button } = await setup();
    expect(button.getAttribute('role')).toBe('switch');
    expect(button.type).toBe('button');
    expect(button.getAttribute('aria-checked')).toBe('true');
    const label = element.querySelector(`#${button.getAttribute('aria-labelledby')}`);
    expect(label?.textContent).toContain('Cocher par défaut');
  });

  it('toggles its value on click', async () => {
    const { fixture, host, button } = await setup();
    button.click();
    await fixture.whenStable();
    expect(host.checked()).toBe(false);
    expect(button.getAttribute('aria-checked')).toBe('false');
  });

  it('does not change when disabled', async () => {
    const { fixture, host, button } = await setup();
    host.disabled.set(true);
    await fixture.whenStable();
    expect(button.disabled).toBe(true);
    button.click();
    expect(host.checked()).toBe(true);
  });
});
