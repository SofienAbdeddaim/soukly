import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiButton, UiButtonSize, UiButtonVariant } from './button';

@Component({
  imports: [UiButton],
  template: `
    <button
      ui-button
      type="button"
      [variant]="variant()"
      [size]="size()"
      [loading]="loading()"
      [disabled]="disabled()"
      (click)="clicks = clicks + 1"
    >
      <svg uiButtonIcon data-testid="icon"></svg>
      Publier
    </button>
    <a ui-button href="#target" [disabled]="disabled()" (click)="linkClicks = linkClicks + 1"
      >Lien</a
    >
  `,
})
class Host {
  variant = signal<UiButtonVariant>('primary');
  size = signal<UiButtonSize>('md');
  loading = signal(false);
  disabled = signal(false);
  clicks = 0;
  linkClicks = 0;
}

describe('UiButton', () => {
  async function setup() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      host: fixture.componentInstance,
      button: element.querySelector('button') as HTMLButtonElement,
      link: element.querySelector('a') as HTMLAnchorElement,
    };
  }

  it('renders the primary medium variant by default with its label and icon', async () => {
    const { button } = await setup();
    expect(button.classList).toContain('ui-button--primary');
    expect(button.classList).not.toContain('ui-button--lg');
    expect(button.textContent).toContain('Publier');
    expect(button.querySelector('[data-testid=icon]')).not.toBeNull();
    expect(button.getAttribute('aria-busy')).toBeNull();
  });

  it.each<UiButtonVariant>(['primary', 'secondary', 'ghost', 'danger'])(
    'applies the %s variant',
    async (variant) => {
      const { fixture, host, button } = await setup();
      host.variant.set(variant);
      host.size.set('lg');
      await fixture.whenStable();
      expect(button.classList).toContain(`ui-button--${variant}`);
      expect(button.classList).toContain('ui-button--lg');
    },
  );

  it('marks a loading button busy, shows a spinner instead of the icon and ignores clicks', async () => {
    const { fixture, host, button } = await setup();
    host.loading.set(true);
    await fixture.whenStable();
    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.disabled).toBe(false); // stays focusable
    expect(button.querySelector('.ui-button__spinner')).not.toBeNull();
    expect(button.querySelector('[data-testid=icon]')).toBeNull();
    button.click();
    expect(host.clicks).toBe(0);
  });

  it('disables a native button', async () => {
    const { fixture, host, button } = await setup();
    host.disabled.set(true);
    await fixture.whenStable();
    expect(button.disabled).toBe(true);
    button.click();
    expect(host.clicks).toBe(0);
  });

  it('disables a link with aria-disabled, removes it from the tab order and ignores clicks', async () => {
    const { fixture, host, link } = await setup();
    host.disabled.set(true);
    await fixture.whenStable();
    expect(link.getAttribute('aria-disabled')).toBe('true');
    expect(link.getAttribute('tabindex')).toBe('-1');
    expect(link.hasAttribute('disabled')).toBe(false);
    link.click();
    expect(host.linkClicks).toBe(0);
  });

  it('lets an enabled button and link handle clicks', async () => {
    const { host, button, link } = await setup();
    button.click();
    link.click();
    expect(host.clicks).toBe(1);
    expect(host.linkClicks).toBe(1);
  });
});
