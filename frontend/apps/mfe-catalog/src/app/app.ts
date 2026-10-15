import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Minimal layout for the standalone launch only, to develop mfe-catalog without the shell. The shell
 * never loads this component: it only loads the exposed routes.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <header class="bg-brand px-4 py-3 text-on-brand">
      <span class="font-display text-2xl text-accent">Soukly</span>
      <span class="ms-2 text-sm">mfe-catalog (standalone)</span>
    </header>
    <main id="main-content">
      <router-outlet />
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
