import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SHELL_ROUTES } from '../shell-routes';
import { shellI18nTesting } from '../testing/shell-i18n-testing';
import { RemoteError } from './remote-error';

describe('RemoteError', () => {
  const createRoutes = vi.fn(() => []);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RemoteError, shellI18nTesting()],
      providers: [provideRouter([]), { provide: SHELL_ROUTES, useValue: createRoutes }],
    });
  });

  it('explains the problem and offers a retry button in the content area', async () => {
    const fixture = TestBed.createComponent(RemoteError);
    fixture.componentRef.setInput('remoteName', 'mfe-publish');
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('momentanément indisponible');
    expect(element.querySelector('button')?.textContent).toContain('Réessayer');
    expect(element.querySelector('[data-testid=remote-error]')?.getAttribute('data-remote')).toBe(
      'mfe-publish',
    );
  });

  it('resets the routes with fresh objects, then navigates again to the same URL', async () => {
    const router = TestBed.inject(Router);
    const resetConfig = vi.spyOn(router, 'resetConfig');
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/fr/publish/new');
    const fixture = TestBed.createComponent(RemoteError);

    fixture.componentInstance['retry']();

    expect(createRoutes).toHaveBeenCalledTimes(1);
    expect(resetConfig).toHaveBeenCalledWith([]);
    expect(navigate).toHaveBeenCalledWith('/fr/publish/new', { onSameUrlNavigation: 'reload' });
  });
});
