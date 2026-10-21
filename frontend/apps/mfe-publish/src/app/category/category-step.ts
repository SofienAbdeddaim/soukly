import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import { UiAlert, UiButton } from '@soukly/ui';
import { DraftStore } from '../draft/draft-store';
import { StepLayout } from '../layout/step-layout/step-layout';
import { stepCommands } from '../shared/publish-steps';
import { CategoryPicker } from './category-picker';

/**
 * Step 1 of an existing draft (`/{lang}/publish/{id}/category`). Another subcategory is saved at
 * once; the API removes what no longer applies and says so in `warnings`, shown here.
 */
@Component({
  selector: 'app-category-step',
  imports: [TranslocoPipe, CategoryPicker, StepLayout, UiAlert, UiButton],
  template: `
    <app-step-layout step="category">
      @for (warning of warnings(); track warning) {
        <ui-alert type="info" data-testid="category-warning">
          {{ 'publish.category.warnings.' + warning | transloco }}
        </ui-alert>
      }
      @if (failed()) {
        <ui-alert type="error">{{ 'publish.category.errors.unexpected' | transloco }}</ui-alert>
      }
      <app-category-picker
        [selected]="store.listing()?.categoryCode ?? null"
        [busy]="saving()"
        (chosen)="change($event)"
      />
      <button
        ui-button
        stepActions
        type="button"
        size="lg"
        class="step__primary"
        [loading]="saving()"
        (click)="next()"
      >
        {{ 'publish.actions.continue' | transloco }}
      </button>
    </app-step-layout>
  `,
  styleUrl: '../shared/step-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryStep {
  protected readonly store = inject(DraftStore);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  protected readonly saving = signal(false);
  protected readonly failed = signal(false);
  /** Automatic changes made by the last change of subcategory. */
  protected readonly warnings = signal<string[]>([]);
  private readonly known = new Set(['attributes-removed', 'price-mode-reset']);

  protected async change(categoryCode: string): Promise<void> {
    this.saving.set(true);
    this.failed.set(false);
    this.warnings.set([]);
    const saved = await this.store.save({ categoryCode });
    this.saving.set(false);
    if (saved) {
      this.warnings.set(
        (this.store.listing()?.warnings ?? []).filter((warning) => this.known.has(warning)),
      );
    } else if (this.store.saveState() !== 'conflict' && this.store.saveState() !== 'offline') {
      this.failed.set(true);
    }
  }

  protected async next(): Promise<void> {
    if (await this.store.flush()) {
      const id = this.store.id();
      if (id) {
        await this.router.navigate(stepCommands(this.locale.lang(), id, 'photos'));
      }
    }
  }
}
