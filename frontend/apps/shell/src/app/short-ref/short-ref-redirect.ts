import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { API_PREFIX } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import { UiButton, UiSkeleton } from '@soukly/ui';
import { firstValueFrom } from 'rxjs';

/** First segment of the short address of a listing, `/r/8F3C2K9A` (SOC-05), without a language. */
export const SHORT_REF_PATH = 'r';

/** Parameters added to the listing page: the visits from the Instagram captions are counted. */
export const INSTAGRAM_TRACKING = {
  utm_source: 'instagram',
  utm_medium: 'social',
  utm_campaign: 'soukly_share',
} as const;

/**
 * The short address of a listing written in the Instagram captions (`/r/{ref}`, SOC-05), where
 * links are not clickable. Asks listing-service for the listing of the reference, in any case,
 * then replaces the address with `/{lang}/listings/{id}` and the Instagram tracking parameters,
 * in the remembered or browser language. An unknown reference opens the "listing not found" page
 * of CATA-03 at `/{lang}/listings/{ref}`.
 */
@Component({
  selector: 'app-short-ref-redirect',
  imports: [TranslocoPipe, UiButton, UiSkeleton],
  template: `
    <section
      class="mx-auto flex max-w-xl flex-col gap-4 px-4 py-12"
      aria-labelledby="short-ref-title"
      [attr.aria-busy]="failed() ? null : 'true'"
      data-testid="short-ref"
    >
      <h1 id="short-ref-title" class="text-2xl text-text">{{ 'short-ref.title' | transloco }}</h1>
      @if (failed()) {
        <p class="text-text-muted" role="alert">{{ 'short-ref.error' | transloco }}</p>
        <div>
          <button ui-button variant="secondary" type="button" (click)="open()">
            {{ 'short-ref.retry' | transloco }}
          </button>
        </div>
      } @else {
        <p class="text-text-muted" role="status">{{ 'short-ref.loading' | transloco }}</p>
        <ui-skeleton variant="card" blockSize="120px" />
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShortRefRedirect implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly locale = inject(LocaleService);

  /** listing-service did not answer: the visitor can try again. */
  readonly failed = signal(false);

  ngOnInit(): void {
    void this.open();
  }

  async open(): Promise<void> {
    this.failed.set(false);
    const ref = this.route.snapshot.paramMap.get('ref') ?? '';
    const lang = this.locale.preferredLang();
    try {
      const { id } = await firstValueFrom(
        this.http.get<{ id: string }>(`${API_PREFIX}/listings/by-ref/${encodeURIComponent(ref)}`),
      );
      await this.router.navigate([`/${lang}/listings/${id}`], {
        queryParams: INSTAGRAM_TRACKING,
        replaceUrl: true,
      });
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        await this.router.navigate([`/${lang}/listings/${ref}`], { replaceUrl: true });
        return;
      }
      this.failed.set(true);
    }
  }
}
