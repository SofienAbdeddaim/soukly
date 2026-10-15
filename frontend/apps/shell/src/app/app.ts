import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { UiSkeleton } from '@soukly/ui';
import { RemoteLoading } from './federation/remote-loading';
import { BottomNav } from './layout/bottom-nav/bottom-nav';
import { Footer } from './layout/footer/footer';
import { Header } from './layout/header/header';
import { SkipLink } from './layout/skip-link/skip-link';

/** Common layout: header, content area where the remotes are rendered, footer, mobile bar. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, UiSkeleton, SkipLink, Header, Footer, BottomNav],
  template: `
    <div class="flex min-h-dvh flex-col pb-16 md:pb-0">
      <app-skip-link />
      <app-header />
      <main
        id="main-content"
        tabindex="-1"
        class="flex-1 outline-none"
        [attr.aria-busy]="loading.active() ? 'true' : null"
      >
        @if (loading.active()) {
          <div
            class="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-8 md:px-6"
            data-testid="remote-loading"
          >
            <ui-skeleton variant="line" inlineSize="40%" blockSize="2.25rem" />
            <ui-skeleton variant="line" inlineSize="70%" />
            <ui-skeleton variant="block" blockSize="12rem" />
          </div>
        }
        <div [hidden]="loading.active()">
          <router-outlet />
        </div>
      </main>
      <app-footer />
      <app-bottom-nav />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly loading = inject(RemoteLoading);
}
