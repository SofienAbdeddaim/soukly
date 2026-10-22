import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService, loginUrlTree } from '@soukly/auth';
import { isLang, LocaleService } from '@soukly/i18n';
import {
  INSTAGRAM_CALLBACK_PATH,
  INSTAGRAM_RESULT_PARAM,
  InstagramCallbackOutcome,
  SOCIAL_PAGE_PATH,
  SocialApi,
} from '@soukly/social-data';
import { UiSkeleton } from '@soukly/ui';

/**
 * Where Instagram sends the member back (`/oauth/instagram/callback`, SOC-02), the redirect URI
 * declared at Meta, without a language prefix like the Facebook one. Reads `code`, `state` or
 * `error`, removes them from the address bar, hands them to social-publisher (the code is never
 * exchanged in the browser), then goes to `/{lang}/account/social` in the language the connection
 * started from, with `?instagram=connected` or the reason of a failure.
 */
@Component({
  selector: 'app-instagram-callback',
  imports: [TranslocoPipe, UiSkeleton],
  template: `
    <section
      class="mx-auto flex max-w-xl flex-col gap-4 px-4 py-12"
      aria-labelledby="instagram-callback-title"
      aria-busy="true"
      data-testid="instagram-callback"
    >
      <h1 id="instagram-callback-title" class="text-2xl text-text">
        {{ 'oauth.instagram.title' | transloco }}
      </h1>
      <p class="text-text-muted" role="status">{{ 'oauth.instagram.loading' | transloco }}</p>
      <ui-skeleton variant="card" blockSize="120px" />
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstagramCallback implements OnInit {
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
    this.location.replaceState(`/${INSTAGRAM_CALLBACK_PATH}`);

    await this.auth.whenRestored();
    if (!this.auth.isAuthenticated()) {
      const lang = this.locale.preferredLang();
      await this.router.navigateByUrl(
        loginUrlTree(this.router, lang, `/${lang}/${SOCIAL_PAGE_PATH}`),
        { replaceUrl: true },
      );
      return;
    }
    await this.leave(await this.api.instagramCallback(redirect));
  }

  private leave(outcome: InstagramCallbackOutcome): Promise<boolean> {
    const lang = isLang(outcome.lang) ? outcome.lang : this.locale.preferredLang();
    const result = outcome.kind === 'connected' ? 'connected' : outcome.failure;
    return this.router.navigate([`/${lang}/${SOCIAL_PAGE_PATH}`], {
      queryParams: { [INSTAGRAM_RESULT_PARAM]: result },
      replaceUrl: true,
    });
  }
}
