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
import { InstagramFailure } from '@soukly/social-data';
import { UiButton } from '@soukly/ui';

/**
 * Why the return from Instagram failed (SOC-02), with what the member can do. A personal account
 * gets the "Personal account" screen: the 3 steps to switch to a professional account in the
 * Instagram app, "Try again" and "Later". Takes the focus when shown, so keyboard and screen reader
 * users land on it.
 */
@Component({
  selector: 'app-instagram-failure',
  imports: [TranslocoPipe, UiButton],
  template: `
    @if (personal()) {
      <span class="failure__logo" aria-hidden="true">IG</span>
    }
    <h2 class="failure__title" id="instagram-failure-title">
      {{ prefix() + '.title' | transloco }}
    </h2>
    <p class="failure__text">{{ prefix() + '.text' | transloco }}</p>
    @if (personal()) {
      <ol class="failure__steps" data-testid="instagram-steps">
        <li>{{ prefix() + '.step-1' | transloco }}</li>
        <li>{{ prefix() + '.step-2' | transloco }}</li>
        <li>{{ prefix() + '.step-3' | transloco }}</li>
      </ol>
    }
    @if (canRetry()) {
      <div class="failure__actions">
        <button ui-button type="button" data-testid="instagram-retry" (click)="retry.emit()">
          {{ 'account.social.failure.' + retryKey() | transloco }}
        </button>
        <button
          ui-button
          variant="ghost"
          type="button"
          data-testid="instagram-failure-dismiss"
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

    :host(.failure--personal) {
      padding-block: 24px;
    }

    .failure__logo {
      display: grid;
      place-items: center;
      align-self: center;
      inline-size: 88px;
      block-size: 88px;
      border-radius: var(--radius-full);
      background-color: var(--color-surface-muted);
      color: var(--color-instagram);
      font-size: 1.875rem;
      font-weight: 700;
    }

    .failure__title {
      font-family: var(--font-body);
      font-size: 1.125rem;
      font-weight: 600;
    }

    :host(.failure--personal) .failure__title {
      font-family: var(--font-display);
      font-size: 1.625rem;
      font-weight: 400;
      text-align: center;
    }

    :host(.failure--personal) .failure__text {
      text-align: center;
    }

    .failure__text {
      line-height: 1.55;
    }

    .failure__steps {
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding-inline-start: 22px;
      list-style: decimal;
      line-height: 1.45;
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
    'aria-labelledby': 'instagram-failure-title',
    '[class.failure--personal]': 'personal()',
    '[attr.data-failure]': 'failure()',
    'data-testid': 'instagram-failure',
  },
})
export class InstagramFailurePanel {
  readonly failure = input.required<InstagramFailure>();
  readonly retry = output<void>();
  readonly dismiss = output<void>();

  protected readonly prefix = computed(() => `account.social.instagram-failure.${this.failure()}`);
  protected readonly personal = computed(() => this.failure() === 'instagram-not-professional');

  /** Nothing to try again while Instagram is not configured in this environment. */
  protected readonly canRetry = computed(() => this.failure() !== 'instagram-not-configured');

  /** "Start again" after an expired link, "Try again" otherwise. */
  protected readonly retryKey = computed(() =>
    this.failure() === 'oauth-state-invalid' ? 'restart' : 'retry',
  );

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef);
    afterNextRender(() => host.nativeElement.focus());
  }
}
