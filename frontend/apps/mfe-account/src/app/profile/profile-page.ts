import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideList, LucideLogOut, LucideShare2 } from '@lucide/angular';
import { AuthService } from '@soukly/auth';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import { UiAlert, UiButton, UiSkeleton } from '@soukly/ui';
import { PageHeader } from '../shared/page-header';
import { Profile, ProfileApi } from './profile-api';
import { ProfileIdentity } from './profile-identity';
import { ProfileInfoForm } from './profile-info-form';
import { SecuritySection } from './security-section';

/**
 * "My profile" (`/{lang}/account/profile`, AUTH-05): who the member is, their details, the email
 * and password, and a discreet link to delete the account.
 */
@Component({
  selector: 'app-profile-page',
  imports: [
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideList,
    LucideLogOut,
    LucideShare2,
    UiAlert,
    UiButton,
    UiSkeleton,
    PageHeader,
    ProfileIdentity,
    ProfileInfoForm,
    SecuritySection,
  ],
  templateUrl: './profile-page.html',
  styleUrl: './profile-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage implements OnInit {
  private readonly api = inject(ProfileApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly profile = signal<Profile | null>(null);
  protected readonly loggingOut = signal(false);

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.state.set('loading');
    try {
      this.profile.set(await this.api.get());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  protected onSaved(profile: Profile): void {
    this.profile.set(profile);
  }

  protected onEmailChangeCancelled(): void {
    this.profile.update((profile) => (profile ? { ...profile, pendingEmail: null } : profile));
  }

  protected async logout(): Promise<void> {
    this.loggingOut.set(true);
    await this.auth.logout();
    await this.router.navigateByUrl(`/${this.locale.lang()}`);
  }
}
