import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiProgress } from './progress';

@Component({
  imports: [UiProgress],
  template: `<ui-progress [value]="value()" label="Progression" valueText="Étape 2 sur 5" />`,
})
class Host {
  value = signal(40);
}

describe('UiProgress', () => {
  it('exposes its value to assistive technologies', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const bar = (fixture.nativeElement as HTMLElement).querySelector('ui-progress') as HTMLElement;

    expect(bar.getAttribute('role')).toBe('progressbar');
    expect(bar.getAttribute('aria-valuenow')).toBe('40');
    expect(bar.getAttribute('aria-valuetext')).toBe('Étape 2 sur 5');
    expect(bar.getAttribute('aria-label')).toBe('Progression');
  });

  it('keeps the value between 0 and 100', async () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.value.set(140);
    await fixture.whenStable();
    const bar = (fixture.nativeElement as HTMLElement).querySelector('ui-progress') as HTMLElement;
    expect(bar.getAttribute('aria-valuenow')).toBe('100');
  });
});
