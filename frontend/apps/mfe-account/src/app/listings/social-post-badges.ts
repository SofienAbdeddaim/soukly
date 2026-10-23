import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import { PostStatusView, SocialNetwork } from '@soukly/social-data';
import { UiBadge, UiBadgeTone, UiButton } from '@soukly/ui';
import { pluralKey } from '../shared/plural';

const TONES: Record<PostStatusView['badge'], UiBadgeTone> = {
  PUBLISHED: 'success',
  PENDING: 'pending',
  TO_RECONNECT: 'warning',
  FAILED: 'danger',
};

/**
 * Where a listing was posted (SOC-06): one pill per network, Facebook first, as social-publisher
 * computes them. A published post links to the post in a new tab; a network to connect again
 * links to "My networks"; a failure gives its reason (never the message of Meta) and "Try again"
 * while tries are left. Nothing at all when the listing has no post.
 */
@Component({
  selector: 'app-social-post-badges',
  imports: [RouterLink, TranslocoPipe, LocalePathPipe, UiBadge, UiButton],
  template: `
    @if (posts().length) {
      <ul
        class="posts"
        [attr.aria-label]="'account.posts.label' | transloco"
        data-testid="post-badges"
      >
        @for (post of posts(); track post.network) {
          @let name = 'account.posts.network.' + post.network | transloco;
          <li [attr.data-network]="post.network" [attr.data-badge]="post.badge">
            @if (post.badge === 'PUBLISHED' && post.permalink) {
              <a
                class="posts__link"
                [href]="post.permalink"
                target="_blank"
                rel="noopener noreferrer"
                [attr.aria-label]="'account.posts.open' | transloco: { network: name }"
                data-testid="post-badge"
              >
                <ui-badge [tone]="tone(post)">
                  <span [class]="markClass(post.network)" aria-hidden="true">{{
                    mark(post.network)
                  }}</span>
                  {{ 'account.posts.badge.' + post.badge | transloco: { network: name } }}
                  <span aria-hidden="true">↗</span>
                </ui-badge>
              </a>
            } @else {
              <ui-badge [tone]="tone(post)" data-testid="post-badge">
                <span [class]="markClass(post.network)" aria-hidden="true">{{
                  mark(post.network)
                }}</span>
                {{ 'account.posts.badge.' + post.badge | transloco: { network: name } }}
              </ui-badge>
            }
          </li>
        }
      </ul>

      @for (post of posts(); track post.network) {
        @let name = 'account.posts.network.' + post.network | transloco;
        @if (post.badge === 'TO_RECONNECT') {
          <p class="posts__note" data-testid="post-reconnect" [attr.data-network]="post.network">
            {{ 'account.posts.reconnect.' + post.network | transloco }}
            <a [routerLink]="'/account/social' | localePath">{{
              'account.posts.reconnect.link' | transloco
            }}</a>
          </p>
        } @else if (post.badge === 'FAILED') {
          <div class="posts__failure" data-testid="post-failure" [attr.data-network]="post.network">
            <p>
              <strong>{{ 'account.posts.failed' | transloco: { network: name } }}</strong>
              {{
                'account.posts.reason.' + (post.reason ?? 'REJECTED') | transloco: { network: name }
              }}
            </p>
            @if (post.retryable) {
              <div class="posts__retry">
                <button
                  ui-button
                  type="button"
                  [loading]="retrying() === post.network"
                  [disabled]="retrying() !== null"
                  [attr.aria-label]="'account.posts.retry-label' | transloco: { network: name }"
                  data-testid="post-retry"
                  (click)="retry.emit(post.network)"
                >
                  {{ 'account.posts.retry' | transloco }}
                </button>
                <span class="posts__muted" data-testid="post-retries-left">
                  {{ retriesLeftKey(post) | transloco: { count: post.manualRetriesLeft } }}
                </span>
              </div>
            } @else {
              <p class="posts__muted" data-testid="post-impossible">
                {{ 'account.posts.impossible' | transloco: { network: name } }}
              </p>
            }
          </div>
        }
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .posts {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      column-gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .posts li {
      display: flex;
      align-items: center;
      min-block-size: 32px;
    }

    /* The whole pill is the link; its target reaches 44px without making the pill taller. */
    .posts__link {
      position: relative;
      display: inline-flex;
      border-radius: var(--radius-full);
      color: inherit;
      text-decoration: none;
    }

    .posts__link::after {
      position: absolute;
      inset-block: -8px;
      inset-inline: 0;
      content: '';
    }

    .posts__link:hover ui-badge {
      text-decoration: underline;
    }

    .posts__link:focus-visible {
      outline: 2px solid transparent;
      box-shadow: var(--focus-ring);
    }

    ui-badge {
      gap: 6px;
      padding-inline-start: 3px;
    }

    .posts__mark {
      display: grid;
      place-items: center;
      inline-size: 18px;
      block-size: 18px;
      border-radius: 5px;
      color: var(--color-on-network);
      font-size: 0.625rem;
      font-weight: 600;
    }

    .posts__mark--facebook {
      background-color: var(--color-facebook);
    }

    .posts__mark--instagram {
      background-color: var(--color-instagram);
    }

    .posts__note {
      font-size: 0.8125rem;
    }

    .posts__note a {
      color: var(--color-primary);
      font-weight: 600;
    }

    .posts__note a:hover {
      color: var(--color-primary-hover);
    }

    .posts__failure {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 10px 12px;
      border-radius: var(--radius-md);
      background-color: var(--color-danger-surface);
      font-size: 0.8125rem;
      line-height: 1.45;
    }

    .posts__retry {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px;
    }

    .posts__muted {
      color: var(--color-text-muted);
      font-size: 0.75rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocialPostBadges {
  readonly posts = input.required<PostStatusView[]>();
  /** The network of this listing being queued again. */
  readonly retrying = input<SocialNetwork | null>(null);
  readonly retry = output<SocialNetwork>();

  private readonly locale = inject(LocaleService);

  protected tone(post: PostStatusView): UiBadgeTone {
    return TONES[post.badge];
  }

  protected mark(network: SocialNetwork): string {
    return network === 'FACEBOOK' ? 'f' : 'IG';
  }

  protected markClass(network: SocialNetwork): string {
    return `posts__mark posts__mark--${network.toLowerCase()}`;
  }

  protected retriesLeftKey(post: PostStatusView): string {
    return pluralKey('account.posts.retries-left', post.manualRetriesLeft, this.locale.lang());
  }
}
