import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideArrowLeft } from '@lucide/angular';
import { LocaleService } from '@soukly/i18n';
import { UiAlert, UiButton, UiProgress } from '@soukly/ui';
import { DraftStore } from '../../draft/draft-store';
import { SaveStatus } from '../save-status/save-status';
import {
  PublishStep,
  previousStep,
  stepCommands,
  stepNumber,
  STEPS,
} from '../../shared/publish-steps';

/**
 * Frame of every step of the form, as on the ANN-04 mockups: an indigo header (back link,
 * "Step n of 5", title of the step, progress bar), the content, then a bar with the saving state
 * and the actions of the step (`<… stepActions>`), kept at the bottom of the screen.
 *
 * The back link leads to the previous step (home from the first one). The title of the step is
 * the `h1` of the page and receives the focus when the step opens, so the change of step is
 * announced. Without a draft (`/publish/new`) there is no saving state.
 */
@Component({
  selector: 'app-step-layout',
  imports: [RouterLink, TranslocoPipe, LucideArrowLeft, SaveStatus, UiAlert, UiButton, UiProgress],
  templateUrl: './step-layout.html',
  styleUrl: './step-layout.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StepLayout {
  readonly step = input.required<PublishStep>();

  protected readonly store = inject(DraftStore, { optional: true });
  private readonly locale = inject(LocaleService);
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  protected readonly total = STEPS.length;
  protected readonly number = computed(() => stepNumber(this.step()));
  protected readonly backCommands = computed(() => {
    const previous = previousStep(this.step());
    const id = this.store?.id();
    return previous && id
      ? stepCommands(this.locale.lang(), id, previous)
      : ['/', this.locale.lang()];
  });

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus({ preventScroll: true }));
  }

  protected reload(): void {
    void this.store?.reload();
  }
}
