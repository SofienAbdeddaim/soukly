import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { UiButton } from '@soukly/ui';
import { SHELL_ROUTES } from '../shell-routes';

/**
 * Shown in the content area when a remote cannot be loaded. "Réessayer" requests the remote again
 * on the same URL, without reloading the page.
 * i18n: SETUP-07
 */
@Component({
  selector: 'app-remote-error',
  imports: [UiButton],
  template: `
    <section
      class="mx-auto flex max-w-6xl flex-col items-start gap-4 px-4 py-12 md:px-6"
      aria-labelledby="remote-error-title"
      data-testid="remote-error"
      [attr.data-remote]="remoteName()"
    >
      <h1 id="remote-error-title" class="text-3xl text-text">
        Cette page est momentanément indisponible
      </h1>
      <p class="max-w-2xl text-text-muted">
        Nous n'arrivons pas à la charger. Vérifiez votre connexion puis réessayez.
      </p>
      <button ui-button type="button" [loading]="retrying()" (click)="retry()">Réessayer</button>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemoteError {
  /** Bound from the route data. */
  readonly remoteName = input<string>();

  protected readonly retrying = signal(false);
  private readonly router = inject(Router);
  private readonly createRoutes = inject(SHELL_ROUTES);

  protected async retry(): Promise<void> {
    this.retrying.set(true);
    this.router.resetConfig(this.createRoutes());
    try {
      await this.router.navigateByUrl(this.router.url, { onSameUrlNavigation: 'reload' });
    } finally {
      this.retrying.set(false);
    }
  }
}
