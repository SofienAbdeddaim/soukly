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
  INSTAGRAM_RESULT_PARAM,
  InstagramFailure,
  SocialApi,
  SocialConnection,
  SocialNetwork,
  toFacebookFailure,
  toInstagramFailure,
} from '@soukly/social-data';
import { UiAlert, UiButton, UiSkeleton } from '@soukly/ui';
import { firstValueFrom } from 'rxjs';
import { AccountToasts } from '../shared/account-toasts';
import { PageHeader } from '../shared/page-header';
import { DisconnectDialog, DisconnectDialogData } from './disconnect-dialog';
import { FacebookCard } from './facebook-card';
import { FacebookFailurePanel } from './facebook-failure';
import { InstagramCard } from './instagram-card';
import { InstagramFailurePanel } from './instagram-failure';

/**
 * "My networks" (`/{lang}/account/social`): the Facebook Page (SOC-01) and the Instagram account
 * (SOC-02) of the member, independent of each other. "Connect" opens the dialog of the network;
 * the shell brings the member back here with `?facebook=` or `?instagram=` `connected` or the
 * reason of a failure, read once then removed from the URL.
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
    InstagramCard,
    InstagramFailurePanel,
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
  protected readonly facebook = computed(() => this.find('FACEBOOK'));
  protected readonly instagram = computed(() => this.find('INSTAGRAM'));
  /** The return from Facebook failed, or a connection could not start. */
  protected readonly failure = signal<FacebookFailure | null>(null);
  /** The return from Facebook connected a Page. */
  protected readonly connected = signal(false);
  /** The Facebook dialog is about to open. */
  protected readonly connecting = signal(false);
  /** The return from Instagram failed, or a connection could not start. */
  protected readonly instagramFailure = signal<InstagramFailure | null>(null);
  /** The return from Instagram connected an account. */
  protected readonly instagramConnected = signal(false);
  /** The Instagram dialog is about to open. */
  protected readonly instagramConnecting = signal(false);
  /** The network whose disconnection failed. */
  protected readonly disconnectError = signal<'facebook' | 'instagram' | null>(null);

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
    this.clearNotices();
    this.connecting.set(true);
    try {
      const authorizeUrl = await this.api.connectFacebook(this.locale.lang());
      this.navigation.go(authorizeUrl);
    } catch (error) {
      this.failure.set(toFacebookFailure(problemCode(error)));
      this.connecting.set(false);
    }
  }

  /** Starts an Instagram connection (also "Change account" and "Connect again"). */
  protected async connectInstagram(): Promise<void> {
    this.clearNotices();
    this.instagramConnecting.set(true);
    try {
      const authorizeUrl = await this.api.connectInstagram(this.locale.lang());
      this.navigation.go(authorizeUrl);
    } catch (error) {
      this.instagramFailure.set(toInstagramFailure(problemCode(error)));
      this.instagramConnecting.set(false);
    }
  }

  protected async disconnect(): Promise<void> {
    const page = this.facebook();
    if (page && (await this.confirmDisconnect('facebook', page.displayName))) {
      await this.remove('FACEBOOK', () => this.api.disconnectFacebook(), 'social.disconnect.done');
    }
  }

  protected async disconnectInstagram(): Promise<void> {
    const account = this.instagram();
    if (account && (await this.confirmDisconnect('instagram', account.displayName))) {
      await this.remove(
        'INSTAGRAM',
        () => this.api.disconnectInstagram(),
        'social.disconnect-instagram.done',
      );
    }
  }

  protected dismissInstagramFailure(): void {
    this.instagramFailure.set(null);
  }

  protected dismissFailure(): void {
    this.failure.set(null);
  }

  private find(network: SocialNetwork): SocialConnection | null {
    return this.connections().find((connection) => connection.network === network) ?? null;
  }

  /** One notice at a time: a new connection or disconnection clears the previous ones. */
  private clearNotices(): void {
    this.failure.set(null);
    this.connected.set(false);
    this.instagramFailure.set(null);
    this.instagramConnected.set(false);
    this.disconnectError.set(null);
  }

  private async confirmDisconnect(
    network: DisconnectDialogData['network'],
    name: string,
  ): Promise<boolean> {
    const dialogRef = this.dialog.open<boolean, DisconnectDialogData, DisconnectDialog>(
      DisconnectDialog,
      {
        data: { network, name },
        injector: this.injector,
        role: 'alertdialog',
        ariaLabelledBy: 'disconnect-dialog-title',
        ariaDescribedBy: 'disconnect-dialog-text',
        maxWidth: '100vw',
      },
    );
    return (await firstValueFrom(dialogRef.closed)) === true;
  }

  /** Deletes the connection of one network; the other one stays. */
  private async remove(
    network: SocialNetwork,
    request: () => Promise<void>,
    doneKey: string,
  ): Promise<void> {
    this.clearNotices();
    try {
      await request();
      this.connections.update((all) => all.filter((connection) => connection.network !== network));
      this.toasts.success(doneKey);
    } catch {
      this.disconnectError.set(network === 'INSTAGRAM' ? 'instagram' : 'facebook');
    }
  }

  /**
   * `?facebook=` or `?instagram=` set by the return routes of the shell: shown once, then removed
   * from the URL.
   */
  private readResult(): void {
    const params = this.route.snapshot.queryParamMap;
    const facebook = params.get(FACEBOOK_RESULT_PARAM);
    const instagram = params.get(INSTAGRAM_RESULT_PARAM);
    if (facebook === null && instagram === null) {
      return;
    }
    if (facebook === 'connected') {
      this.connected.set(true);
    } else if (facebook !== null) {
      this.failure.set(toFacebookFailure(facebook));
    }
    if (instagram === 'connected') {
      this.instagramConnected.set(true);
    } else if (instagram !== null) {
      this.instagramFailure.set(toInstagramFailure(instagram));
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [FACEBOOK_RESULT_PARAM]: null, [INSTAGRAM_RESULT_PARAM]: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
