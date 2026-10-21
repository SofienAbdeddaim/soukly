import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiBadge, UiBadgeTone } from './badge';

@Component({
  imports: [UiBadge],
  template: `<ui-badge [tone]="tone()">Expire dans 2 j</ui-badge>`,
})
class Host {
  tone = signal<UiBadgeTone>('warning');
}

describe('UiBadge', () => {
  it('shows its text with the class of its tone', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const badge = (fixture.nativeElement as HTMLElement).querySelector('ui-badge') as HTMLElement;
    expect(badge.textContent).toBe('Expire dans 2 j');
    expect(badge.classList).toContain('ui-badge--warning');

    fixture.componentInstance.tone.set('neutral');
    await fixture.whenStable();
    expect(badge.classList).toContain('ui-badge--neutral');
    expect(badge.classList).not.toContain('ui-badge--warning');
  });
});
