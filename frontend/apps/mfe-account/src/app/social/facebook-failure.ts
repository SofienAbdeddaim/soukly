import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { FacebookFailure } from '@soukly/social-data';
import { UiButton } from '@soukly/ui';

/** Where Facebook explains how to create a Page; opened in a new tab. */
export const FACEBOOK_CREATE_PAGE_URL = 'https://www.facebook.com/pages/create';

/**
 * Why the return from Facebook failed (SOC-01), with what the member can do: try again, start
 * again after an expired link, or create a Page first when they manage none. Takes the focus when
 * shown, so keyboard and screen reader users land on it.
 */
@Component({
  selector: 'app-facebook-failure',
  imports: [TranslocoPipe, UiButton],
  template: `
    @if (failure() === 'facebook-no-page') {
      <span class="failure__logo" aria-hidden="true">f</span>
    }
    <h2 class="failure__title" id="facebook-failure-title">
      {{ 'account.social.failure.' + failure() + '.title' | transloco }}
    </h2>
    <p class="failure__text">{{ 'account.social.failure.' + failure() + '.text' | transloco }}</p>
    @if (failure() === 'facebook-no-page') {
      <a
        class="failure__help"
        [href]="createPageUrl"
        target="_blank"
        rel="noopener noreferrer"
        data-testid="facebook-create-page"
      >
        {{ 'account.social.failure.facebook-no-page.help' | transloco }}
      </a>
    }
    @if (canRetry()) {
      <div class="failure__actions">
        <button ui-button type="button" data-testid="facebook-retry" (click)="retry.emit()">
          {{ 'account.social.failure.' + retryKey() | transloco }}
        </button>
        <button
          ui-button
          variant="ghost"
          type="button"
          data-testid="facebook-failure-dismiss"
          (click)="dismiss.emit()"
        >
          {{ 'account.social.failure.later' | transloco }}
        </button>
      </div>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 16px;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      background-color: var(--color-surface);
    }

    :host(:focus-visible) {
      outline: none;
      box-shadow: var(--focus-ring);
    }

    :host(.failure--no-page) {
      align-items: center;
      padding-block: 24px;
      text-align: center;
    }

    .failure__logo {
      display: grid;
      place-items: center;
      inline-size: 88px;
      block-size: 88px;
      border-radius: var(--radius-full);
      background-color: var(--color-surface-muted);
      color: var(--color-facebook);
      font-size: 2.5rem;
      font-weight: 700;
    }

    .failure__title {
      font-family: var(--font-body);
      font-size: 1.125rem;
      font-weight: 600;
    }

    :host(.failure--no-page) .failure__title {
      font-family: var(--font-display);
      font-size: 1.625rem;
      font-weight: 400;
    }

    .failure__text {
      line-height: 1.55;
    }

    .failure__help {
      display: inline-flex;
      align-items: center;
      min-block-size: 44px;
      color: var(--color-primary);
      font-weight: 500;
    }

    .failure__actions {
      display: flex;
      flex-direction: column;
      gap: 8px;
      align-self: stretch;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'alert',
    tabindex: '-1',
    'aria-labelledby': 'facebook-failure-title',
    '[class.failure--no-page]': "failure() === 'facebook-no-page'",
    '[attr.data-failure]': 'failure()',
    'data-testid': 'facebook-failure',
  },
})
export class FacebookFailurePanel {
  readonly failure = input.required<FacebookFailure>();
  readonly retry = output<void>();
  readonly dismiss = output<void>();

  protected readonly createPageUrl = FACEBOOK_CREATE_PAGE_URL;

  /** Nothing to try again while Facebook is not configured in this environment. */
  protected readonly canRetry = computed(() => this.failure() !== 'facebook-not-configured');

  /** "Start again" after an expired link, "Try again" otherwise. */
  protected readonly retryKey = computed(() =>
    this.failure() === 'oauth-state-invalid' || this.failure() === 'page-selection-not-found'
      ? 'restart'
      : 'retry',
  );

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef);
    afterNextRender(() => host.nativeElement.focus());
  }
}
