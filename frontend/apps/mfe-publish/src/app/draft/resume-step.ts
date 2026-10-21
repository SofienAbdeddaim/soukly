import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { LocaleService } from '@soukly/i18n';
import { firstIncompleteStep, stepCommands } from '../shared/publish-steps';
import { DraftStore } from './draft-store';

/**
 * `/{lang}/publish/{id}` without a step (resumed from "My listings", ANN-05): opens the first
 * incomplete step of the draft, the preview when nothing is missing.
 */
@Component({
  selector: 'app-resume-step',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResumeStep {
  constructor() {
    const store = inject(DraftStore);
    const listing = store.listing();
    if (listing) {
      void inject(Router).navigate(
        stepCommands(inject(LocaleService).lang(), listing.id, firstIncompleteStep(listing)),
        { replaceUrl: true },
      );
    }
  }
}
