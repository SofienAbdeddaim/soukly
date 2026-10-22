import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, CanActivateFn, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService, loginUrlTree } from '@soukly/auth';
import { isLang, LangActivation, LocaleService } from '@soukly/i18n';
import {
  FACEBOOK_CALLBACK_PATH,
  FACEBOOK_PAGE_CHOICE_PATH,
  FACEBOOK_RESULT_PARAM,
  FacebookCallbackOutcome,
  SOCIAL_PAGE_PATH,
  SocialApi,
} from '@soukly/social-data';
import { UiSkeleton } from '@soukly/ui';

/** The route has no language prefix: the page waits in the language the visitor prefers. */
export const preferredLangGuard: CanActivateFn = async () => {
  const activation = inject(LangActivation);
  const lang = inject(LocaleService).preferredLang();
  await activation.activate(lang);
  return true;
};

/**
 * Where Facebook sends the member back (`/oauth/facebook/callback`, SOC-01), the only redirect URI
 * declared at Meta, hence without a language prefix. Reads `code`, `state` or `error`, removes
 * them from the address bar, hands them to social-publisher (the code is never exchanged in the
 * browser), then goes to `/{lang}/account/social` in the language the connection started from:
 * with `?facebook=connected` or the reason of a failure, or to the choice of a Page.
 */
@Component({
  selector: 'app-facebook-callback',
  imports: [TranslocoPipe, UiSkeleton],
  template: `
    <section
      class="mx-auto flex max-w-xl flex-col gap-4 px-4 py-12"
      aria-labelledby="facebook-callback-title"
      aria-busy="true"
      data-testid="facebook-callback"
    >
      <h1 id="facebook-callback-title" class="text-2xl text-text">
        {{ 'oauth.facebook.title' | transloco }}
      </h1>
      <p class="text-text-muted" role="status">{{ 'oauth.facebook.loading' | transloco }}</p>
      <ui-skeleton variant="card" blockSize="120px" />
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FacebookCallback implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly auth = inject(AuthService);
  private readonly locale = inject(LocaleService);
  private readonly api = inject(SocialApi);

  ngOnInit(): void {
    void this.finish();
  }

  private async finish(): Promise<void> {
    const params = this.route.snapshot.queryParamMap;
    const redirect = {
      code: params.get('code'),
      state: params.get('state'),
      error: params.get('error'),
    };
    // The code and the state never stay in the address bar, the history or a later Referer.
    this.location.replaceState(`/${FACEBOOK_CALLBACK_PATH}`);

    await this.auth.whenRestored();
    if (!this.auth.isAuthenticated()) {
      const lang = this.locale.preferredLang();
      await this.router.navigateByUrl(
        loginUrlTree(this.router, lang, `/${lang}/${SOCIAL_PAGE_PATH}`),
        {
          replaceUrl: true,
        },
      );
      return;
    }
    await this.leave(await this.api.facebookCallback(redirect));
  }

  private leave(outcome: FacebookCallbackOutcome): Promise<boolean> {
    const lang = isLang(outcome.lang) ? outcome.lang : this.locale.preferredLang();
    if (outcome.kind === 'choose') {
      return this.router.navigateByUrl(`/${lang}/${FACEBOOK_PAGE_CHOICE_PATH}`, {
        replaceUrl: true,
      });
    }
    const result = outcome.kind === 'connected' ? 'connected' : outcome.failure;
    return this.router.navigate([`/${lang}/${SOCIAL_PAGE_PATH}`], {
      queryParams: { [FACEBOOK_RESULT_PARAM]: result },
      replaceUrl: true,
    });
  }
}
