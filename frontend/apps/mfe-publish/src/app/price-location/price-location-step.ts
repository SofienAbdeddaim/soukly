import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import { UiButton } from '@soukly/ui';
import { DraftStore } from '../draft/draft-store';
import { StepLayout } from '../layout/step-layout/step-layout';
import { stepCommands } from '../shared/publish-steps';
import { PriceLocationSection } from './price-location-section';

/**
 * Step 4 (`/{lang}/publish/{id}/price-location`): the price and place section, saved as the
 * member types, then "Continue" to the preview once every field is valid and saved.
 *
 * The "Share the listing" block of the mockup waits for SOC-03 and is not shown.
 */
@Component({
  selector: 'app-price-location-step',
  imports: [TranslocoPipe, StepLayout, UiButton, PriceLocationSection],
  template: `
    <app-step-layout step="price-location">
      <app-price-location-section (submitted)="next()" />

      <button
        ui-button
        stepActions
        type="button"
        size="lg"
        class="step__primary"
        data-testid="continue"
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
export class PriceLocationStep {
  private readonly store = inject(DraftStore);
  private readonly locale = inject(LocaleService);
  private readonly router = inject(Router);
  private readonly section = viewChild.required(PriceLocationSection);

  protected readonly saving = signal(false);

  protected async next(): Promise<void> {
    const section = this.section();
    if (!section.validate()) {
      return;
    }
    this.saving.set(true);
    section.saveChanges();
    const saved = await this.store.flush();
    this.saving.set(false);
    const id = this.store.id();
    if (saved && id) {
      await this.router.navigate(stepCommands(this.locale.lang(), id, 'preview'));
    } else {
      section.focusFirstError();
    }
  }
}
