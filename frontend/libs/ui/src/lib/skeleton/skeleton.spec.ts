import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiSkeleton, UiSkeletonVariant } from './skeleton';

@Component({
  imports: [UiSkeleton],
  template: `<ui-skeleton class="extra" [variant]="variant()" inlineSize="60%" />`,
})
class Host {
  variant = signal<UiSkeletonVariant>('line');
}

describe('UiSkeleton', () => {
  async function setup() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      host: fixture.componentInstance,
      skeleton: element.querySelector('ui-skeleton') as HTMLElement,
    };
  }

  it('is hidden from assistive technologies', async () => {
    const { skeleton } = await setup();
    expect(skeleton.getAttribute('aria-hidden')).toBe('true');
  });

  it('renders a line by default with the given size, keeping the host classes', async () => {
    const { skeleton } = await setup();
    expect(skeleton.classList).toContain('ui-skeleton--line');
    expect(skeleton.classList).toContain('extra');
    expect(skeleton.style.inlineSize).toBe('60%');
    expect(skeleton.querySelectorAll('.bone--line').length).toBe(1);
  });

  it('renders a block', async () => {
    const { fixture, host, skeleton } = await setup();
    host.variant.set('block');
    await fixture.whenStable();
    expect(skeleton.classList).toContain('ui-skeleton--block');
    expect(skeleton.querySelectorAll('.bone').length).toBe(1);
    expect(skeleton.querySelector('.bone--line')).toBeNull();
  });

  it('renders a listing card shape: media and three lines', async () => {
    const { fixture, host, skeleton } = await setup();
    host.variant.set('card');
    await fixture.whenStable();
    expect(skeleton.querySelector('.bone--media')).not.toBeNull();
    expect(skeleton.querySelectorAll('.bone--line').length).toBe(3);
  });
});
