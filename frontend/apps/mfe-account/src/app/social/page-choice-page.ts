import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { problemCode } from '@soukly/auth';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import {
  FACEBOOK_RESULT_PARAM,
  PageCandidate,
  SOCIAL_PAGE_PATH,
  SocialApi,
} from '@soukly/social-data';
import { UiAlert, UiButton, UiSkeleton } from '@soukly/ui';
import { PageHeader } from '../shared/page-header';

/**
 * "Choose a Page" (`/{lang}/account/social/facebook/page`, SOC-01): the member manages several
 * Pages, they pick the one Soukly shares on. Radio buttons in a fieldset with a legend. The list
 * comes from the server (tokens stay there) and expires after 10 minutes.
 */
@Component({
  selector: 'app-page-choice-page',
  imports: [
    FormsModule,
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    UiAlert,
    UiButton,
    UiSkeleton,
    PageHeader,
  ],
  templateUrl: './page-choice-page.html',
  styleUrl: './page-choice-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageChoicePage implements OnInit {
  private readonly api = inject(SocialApi);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  protected readonly socialPath = `/${SOCIAL_PAGE_PATH}`;
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly pages = signal<PageCandidate[]>([]);
  protected readonly selected = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal(false);

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.state.set('loading');
    try {
      const pages = await this.api.pageSelection();
      if (pages === null) {
        await this.backToSocial('page-selection-not-found');
        return;
      }
      this.pages.set(pages);
      this.selected.set(pages[0]?.pageId ?? null);
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  protected async choose(): Promise<void> {
    const pageId = this.selected();
    if (!pageId || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.saveError.set(false);
    try {
      await this.api.chooseFacebookPage(pageId);
      await this.backToSocial('connected');
    } catch (error) {
      if (problemCode(error) === 'page-selection-not-found') {
        await this.backToSocial('page-selection-not-found');
        return;
      }
      this.saveError.set(true);
      this.saving.set(false);
    }
  }

  private backToSocial(result: string): Promise<boolean> {
    return this.router.navigate([`/${this.locale.lang()}${this.socialPath}`], {
      queryParams: { [FACEBOOK_RESULT_PARAM]: result },
      replaceUrl: true,
    });
  }
}
