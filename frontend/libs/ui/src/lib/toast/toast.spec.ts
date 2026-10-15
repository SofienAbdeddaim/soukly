import { TestBed } from '@angular/core/testing';
import { ToastService, UI_TOAST_DEFAULT_DURATION, UI_TOAST_REGION_DELAY } from './toast';

describe('ToastService', () => {
  let service: ToastService;

  const region = (live: 'polite' | 'assertive') =>
    document.querySelector(`[aria-live=${live}]`) as HTMLElement | null;

  function render(): void {
    TestBed.tick();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    service = TestBed.inject(ToastService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows an error toast in an assertive live region, after the regions exist', async () => {
    service.show({ type: 'error', message: 'La publication a échoué.', closeLabel: 'Fermer' });
    render();
    expect(region('assertive')).not.toBeNull();
    expect(region('assertive')?.textContent).not.toContain('La publication a échoué.');

    vi.advanceTimersByTime(UI_TOAST_REGION_DELAY);
    render();
    expect(region('assertive')?.textContent).toContain('La publication a échoué.');
    expect(region('polite')?.textContent).not.toContain('La publication a échoué.');
  });

  it('shows info and success toasts in a polite live region', async () => {
    service.show({ message: 'Brouillon enregistré.', closeLabel: 'Fermer' });
    service.show({ type: 'success', message: 'Annonce publiée.', closeLabel: 'Fermer' });
    vi.advanceTimersByTime(UI_TOAST_REGION_DELAY);
    render();
    expect(region('polite')?.textContent).toContain('Brouillon enregistré.');
    expect(region('polite')?.textContent).toContain('Annonce publiée.');
    expect(region('polite')?.querySelector('.ui-toast--success')).not.toBeNull();
    expect(region('assertive')?.textContent?.trim()).toBe('');
  });

  it('closes a toast with its close button', async () => {
    service.show({ type: 'error', message: 'Erreur', closeLabel: 'Fermer' });
    vi.advanceTimersByTime(UI_TOAST_REGION_DELAY);
    render();
    const close = region('assertive')?.querySelector(
      'button[aria-label=Fermer]',
    ) as HTMLButtonElement;
    expect(close).not.toBeNull();
    close.click();
    render();
    expect(service.toasts()).toEqual([]);
    expect(region('assertive')?.textContent).not.toContain('Erreur');
  });

  it('closes a toast on its own after 5 seconds', async () => {
    service.show({ message: 'Info', closeLabel: 'Fermer' });
    vi.advanceTimersByTime(UI_TOAST_REGION_DELAY);
    expect(service.toasts().length).toBe(1);
    vi.advanceTimersByTime(UI_TOAST_DEFAULT_DURATION - 1);
    expect(service.toasts().length).toBe(1);
    vi.advanceTimersByTime(1);
    expect(service.toasts().length).toBe(0);
  });

  it('pauses the timer while the toast is hovered', async () => {
    service.show({ message: 'Info', closeLabel: 'Fermer' });
    vi.advanceTimersByTime(UI_TOAST_REGION_DELAY);
    render();
    const toast = document.querySelector('ui-toast') as HTMLElement;
    vi.advanceTimersByTime(1000);
    toast.dispatchEvent(new MouseEvent('mouseenter'));
    vi.advanceTimersByTime(UI_TOAST_DEFAULT_DURATION);
    expect(service.toasts().length).toBe(1);
    toast.dispatchEvent(new MouseEvent('mouseleave'));
    vi.advanceTimersByTime(UI_TOAST_DEFAULT_DURATION - 1000);
    expect(service.toasts().length).toBe(0);
  });

  it('keeps a toast with duration 0 until it is dismissed', async () => {
    const id = service.show({ message: 'Info', closeLabel: 'Fermer', duration: 0 });
    vi.advanceTimersByTime(UI_TOAST_REGION_DELAY + 60_000);
    expect(service.toasts().length).toBe(1);
    service.dismiss(id);
    expect(service.toasts().length).toBe(0);
  });
});
