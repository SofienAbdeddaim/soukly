import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { problemCode } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import { ListingsApi } from '@soukly/listing-data';
import { UiAlert } from '@soukly/ui';
import { StepLayout } from '../layout/step-layout/step-layout';
import { stepCommands } from '../shared/publish-steps';
import { CategoryPicker } from './category-picker';

/**
 * Step 1 without a draft (`/{lang}/publish/new`). The draft is created when the subcategory is
 * chosen, then the member goes on to the photos; `/new` is replaced in the history by the
 * category step of the draft, so the back button of the browser never creates a second draft.
 */
@Component({
  selector: 'app-new-listing-page',
  imports: [TranslocoPipe, CategoryPicker, StepLayout, UiAlert],
  template: `
    <app-step-layout step="category">
      @if (error(); as code) {
        <ui-alert type="error" data-testid="create-error">
          {{ 'publish.category.errors.' + code | transloco }}
        </ui-alert>
      }
      <app-category-picker [busy]="creating()" (chosen)="create($event)" />
      @if (creating()) {
        <p class="step__muted" role="status">{{ 'publish.category.creating' | transloco }}</p>
      }
    </app-step-layout>
  `,
  styleUrl: '../shared/step-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NewListingPage {
  private readonly api = inject(ListingsApi);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  protected readonly creating = signal(false);
  protected readonly error = signal<'draft-limit-reached' | 'unexpected' | null>(null);

  protected async create(categoryCode: string): Promise<void> {
    if (this.creating()) {
      return;
    }
    this.creating.set(true);
    this.error.set(null);
    try {
      const { listing } = await this.api.create(categoryCode);
      const lang = this.locale.lang();
      await this.router.navigate(stepCommands(lang, listing.id, 'category'), { replaceUrl: true });
      await this.router.navigate(stepCommands(lang, listing.id, 'photos'));
    } catch (error) {
      this.error.set(
        problemCode(error) === 'draft-limit-reached' ? 'draft-limit-reached' : 'unexpected',
      );
    } finally {
      this.creating.set(false);
    }
  }
}
