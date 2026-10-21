import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService, safeReturnUrl } from '@soukly/auth';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import { UiButton } from '@soukly/ui';

interface Network {
  id: 'facebook' | 'instagram';
  /** Letters of the logo placeholder: no brand asset or colour in V1. */
  initials: string;
}

/**
 * Optional screen after the very first login (`/{lang}/account/welcome`): offers to link Facebook and
 * Instagram. "Later" and "Continue to Soukly" go to the page asked for before the login, or home.
 * "Link" opens a waiting page until SOC-01 and SOC-02 wire it.
 */
@Component({
  selector: 'app-welcome-page',
  imports: [RouterLink, TranslocoPipe, LocalePathPipe, UiButton],
  templateUrl: './welcome-page.html',
  styleUrl: './welcome-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WelcomePage {
  protected readonly auth = inject(AuthService);
  private readonly locale = inject(LocaleService);

  protected readonly networks: Network[] = [
    { id: 'facebook', initials: 'f' },
    { id: 'instagram', initials: 'IG' },
  ];

  /** Where "Later" and "Continue" lead. */
  protected readonly next =
    safeReturnUrl(inject(ActivatedRoute).snapshot.queryParamMap.get('returnUrl')) ??
    `/${this.locale.lang()}`;
}
