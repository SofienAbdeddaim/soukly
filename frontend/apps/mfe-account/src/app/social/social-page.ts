import { Dialog } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  Injector,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { problemCode } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import {
  ExternalNavigation,
  FACEBOOK_RESULT_PARAM,
  FacebookFailure,
  SocialApi,
  SocialConnection,
  toFacebookFailure,
} from '@soukly/social-data';
import { UiAlert, UiButton, UiSkeleton } from '@soukly/ui';
import { firstValueFrom } from 'rxjs';
import { AccountToasts } from '../shared/account-toasts';
import { PageHeader } from '../shared/page-header';
import { DisconnectDialog, DisconnectDialogData } from './disconnect-dialog';
import { FacebookCard } from './facebook-card';
import { FacebookFailurePanel } from './facebook-failure';
import { NetworkCard } from './network-card';

/**
 * "My networks" (`/{lang}/account/social`, SOC-01): the Facebook Page of the member and, soon,
 * Instagram (SOC-02). "Connect" opens the Facebook dialog; the shell brings the member back here
 * with `?facebook=connected` or the reason of a failure, read once then removed from the URL.
 */
@Component({
  selector: 'app-social-page',
  imports: [
    TranslocoPipe,
    UiAlert,
    UiButton,
    UiSkeleton,
    PageHeader,
    FacebookCard,
    FacebookFailurePanel,
    NetworkCard,
  ],
  templateUrl: './social-page.html',
  styleUrl: './social-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocialPage implements OnInit {
  private readonly api = inject(SocialApi);
  private readonly dialog = inject(Dialog);
  private readonly injector = inject(Injector);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly toasts = inject(AccountToasts);
  private readonly navigation = inject(ExternalNavigation);

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly connections = signal<SocialConnection[]>([]);
  protected readonly facebook = computed(
    () => this.connections().find((connection) => connection.network === 'FACEBOOK') ?? null,
  );
  /** The return from Facebook failed, or a connection could not start. */
  protected readonly failure = signal<FacebookFailure | null>(null);
  /** The return from Facebook connected a Page. */
  protected readonly connected = signal(false);
  /** The Facebook dialog is about to open. */
  protected readonly connecting = signal(false);
  protected readonly disconnectError = signal(false);

  ngOnInit(): void {
    this.readResult();
    void this.load();
  }

  protected async load(): Promise<void> {
    this.state.set('loading');
    try {
      this.connections.set(await this.api.connections());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  /** Starts a connection (also "Change Page" and "Connect again"): the browser leaves for Facebook. */
  protected async connect(): Promise<void> {
    this.connecting.set(true);
    this.failure.set(null);
    this.connected.set(false);
    try {
      const authorizeUrl = await this.api.connectFacebook(this.locale.lang());
      this.navigation.go(authorizeUrl);
    } catch (error) {
      this.failure.set(toFacebookFailure(problemCode(error)));
      this.connecting.set(false);
    }
  }

  protected async disconnect(): Promise<void> {
    const page = this.facebook();
    if (!page) {
      return;
    }
    const dialogRef = this.dialog.open<boolean, DisconnectDialogData, DisconnectDialog>(
      DisconnectDialog,
      {
        data: { pageName: page.displayName },
        injector: this.injector,
        role: 'alertdialog',
        ariaLabelledBy: 'disconnect-dialog-title',
        ariaDescribedBy: 'disconnect-dialog-text',
        maxWidth: '100vw',
      },
    );
    if (!(await firstValueFrom(dialogRef.closed))) {
      return;
    }
    this.disconnectError.set(false);
    try {
      await this.api.disconnectFacebook();
      this.connections.update((all) =>
        all.filter((connection) => connection.network !== 'FACEBOOK'),
      );
      this.connected.set(false);
      this.toasts.success('social.disconnect.done');
    } catch {
      this.disconnectError.set(true);
    }
  }

  protected dismissFailure(): void {
    this.failure.set(null);
  }

  /** `?facebook=` set by the return route of the shell: shown once, then removed from the URL. */
  private readResult(): void {
    const result = this.route.snapshot.queryParamMap.get(FACEBOOK_RESULT_PARAM);
    if (result === null) {
      return;
    }
    if (result === 'connected') {
      this.connected.set(true);
    } else {
      this.failure.set(toFacebookFailure(result));
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [FACEBOOK_RESULT_PARAM]: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
