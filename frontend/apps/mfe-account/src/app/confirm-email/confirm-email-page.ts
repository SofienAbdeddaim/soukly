import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideCircleCheck, LucideLink2Off, LucideTriangleAlert } from '@lucide/angular';
import { problemCode } from '@soukly/auth';
import { LocalePathPipe } from '@soukly/i18n';
import { UiButton, UiSkeleton } from '@soukly/ui';
import { ProfileApi } from '../profile/profile-api';

type ConfirmState = 'checking' | 'confirmed' | 'invalid' | 'taken' | 'error';

/**
 * Link of the email change (`/{lang}/account/confirm-email?token=…`, AUTH-05), which can be opened
 * on any device, signed in or not. The token is read, removed from the address bar and kept in
 * memory; while the page is open no `Referer` is sent. The address of the account changes at once.
 */
@Component({
  selector: 'app-confirm-email-page',
  imports: [
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideCircleCheck,
    LucideLink2Off,
    LucideTriangleAlert,
    UiButton,
    UiSkeleton,
  ],
  templateUrl: './confirm-email-page.html',
  styleUrl: '../account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmEmailPage implements OnInit {
  private readonly api = inject(ProfileApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly meta = inject(Meta);
  private token: string | null = null;

  protected readonly state = signal<ConfirmState>('checking');

  constructor() {
    // The token must never leave in a Referer, even to a resource loaded before it is removed.
    this.meta.updateTag({ name: 'referrer', content: 'no-referrer' });
    inject(DestroyRef).onDestroy(() => this.meta.removeTag('name="referrer"'));
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (this.token) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { token: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
    void this.confirm();
  }

  protected async confirm(): Promise<void> {
    if (!this.token) {
      this.state.set('invalid');
      return;
    }
    this.state.set('checking');
    try {
      await this.api.confirmEmail(this.token);
      this.state.set('confirmed');
    } catch (error) {
      const code = problemCode(error);
      this.state.set(
        code === 'email-change-token-invalid'
          ? 'invalid'
          : code === 'email-taken'
            ? 'taken'
            : 'error',
      );
    }
  }
}
