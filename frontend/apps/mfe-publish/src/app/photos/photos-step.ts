import { ChangeDetectionStrategy, Component, inject, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import { UiButton } from '@soukly/ui';
import { DraftStore } from '../draft/draft-store';
import { StepLayout } from '../layout/step-layout/step-layout';
import { stepCommands } from '../shared/publish-steps';
import { PhotosSection } from './photos-section';

/**
 * Step 2 (`/{lang}/publish/{id}/photos`): the photos section, then "Continue" once at least one
 * photo is uploaded and none is still uploading.
 */
@Component({
  selector: 'app-photos-step',
  imports: [TranslocoPipe, StepLayout, UiButton, PhotosSection],
  template: `
    <app-step-layout step="photos">
      <app-photos-section />

      <button
        ui-button
        stepActions
        type="button"
        size="lg"
        class="step__primary"
        data-testid="continue"
        (click)="next()"
      >
        {{ 'publish.actions.continue' | transloco }}
      </button>
    </app-step-layout>
  `,
  styleUrl: '../shared/step-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhotosStep {
  private readonly store = inject(DraftStore);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly section = viewChild.required(PhotosSection);

  protected async next(): Promise<void> {
    if (!this.section().check()) {
      return;
    }
    const id = this.store.id();
    if (id && (await this.store.flush())) {
      await this.router.navigate(stepCommands(this.locale.lang(), id, 'details'));
    }
  }
}
