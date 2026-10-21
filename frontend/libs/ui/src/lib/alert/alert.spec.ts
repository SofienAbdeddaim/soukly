import { Component, signal, viewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiAlert, UiAlertType } from './alert';

@Component({
  imports: [UiAlert],
  template: `<ui-alert [type]="type()" [live]="live()"
    >E-mail ou mot de passe incorrect.</ui-alert
  >`,
})
class Host {
  type = signal<UiAlertType>('error');
  live = signal(true);
  alert = viewChild.required(UiAlert);
}

describe('UiAlert', () => {
  async function setup() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const element = (fixture.nativeElement as HTMLElement).querySelector('ui-alert') as HTMLElement;
    return { fixture, host: fixture.componentInstance, element };
  }

  it('announces an error at once and shows its text', async () => {
    const { element } = await setup();
    expect(element.getAttribute('role')).toBe('alert');
    expect(element.classList).toContain('ui-alert--error');
    expect(element.textContent).toContain('E-mail ou mot de passe incorrect.');
  });

  it('is a polite status for the other types, and silent when not live', async () => {
    const { fixture, host, element } = await setup();
    host.type.set('success');
    await fixture.whenStable();
    expect(element.getAttribute('role')).toBe('status');

    host.live.set(false);
    await fixture.whenStable();
    expect(element.getAttribute('role')).toBeNull();
  });

  it('can receive the focus', async () => {
    const { host, element } = await setup();
    host.alert().focus();
    expect(document.activeElement).toBe(element);
  });
});
