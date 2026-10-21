import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocalePathPipe } from '@soukly/i18n';
import { UiAlert, UiButton, UiSkeleton } from '@soukly/ui';
import { DraftStore } from './draft-store';

/**
 * Parent of the steps of a draft (`/{lang}/publish/{id}/…`): reads the draft once for all its
 * steps (a reloaded step URL restores it from the API) and shares it through `DraftStore`.
 * Another id in the URL (same component reused by the router) loads that draft.
 */
@Component({
  selector: 'app-draft-page',
  imports: [RouterOutlet, RouterLink, TranslocoPipe, LocalePathPipe, UiAlert, UiButton, UiSkeleton],
  providers: [DraftStore],
  template: `
    @switch (store.loadState()) {
      @case ('ready') {
        <router-outlet />
      }
      @case ('loading') {
        <div class="draft__state" aria-busy="true" data-testid="draft-loading">
          <ui-skeleton variant="block" blockSize="88px" />
          <ui-skeleton variant="line" inlineSize="60%" blockSize="2rem" />
          <ui-skeleton variant="block" blockSize="12rem" />
        </div>
      }
      @default {
        <div class="draft__state" data-testid="draft-error">
          <ui-alert type="error">
            {{
              (store.loadState() === 'not-found' ? 'publish.load.not-found' : 'publish.load.error')
                | transloco
            }}
          </ui-alert>
          <div class="draft__actions">
            @if (store.loadState() === 'error') {
              <button ui-button variant="secondary" type="button" (click)="retry()">
                {{ 'publish.actions.retry' | transloco }}
              </button>
            }
            <a ui-button [routerLink]="'/publish/new' | localePath">
              {{ 'publish.load.new' | transloco }}
            </a>
          </div>
        </div>
      }
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .draft__state {
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-inline-size: 640px;
      margin-inline: auto;
      padding-block: 24px;
      padding-inline: 16px;
    }

    .draft__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DraftPage {
  protected readonly store = inject(DraftStore);
  private readonly route = inject(ActivatedRoute);
  private id = '';

  constructor() {
    const subscription = this.route.paramMap.subscribe((params) => {
      this.id = params.get('id') ?? '';
      void this.store.load(this.id);
    });
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }

  protected retry(): void {
    void this.store.load(this.id);
  }
}
