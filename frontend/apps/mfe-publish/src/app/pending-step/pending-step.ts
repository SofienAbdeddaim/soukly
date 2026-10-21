import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { StepLayout } from '../layout/step-layout/step-layout';
import { PublishStep } from '../shared/publish-steps';

/**
 * Steps 4 and 5 until the second part of ANN-04 replaces them: the frame of the step, so the
 * flow can already be followed, and a waiting text.
 */
@Component({
  selector: 'app-pending-step',
  imports: [TranslocoPipe, StepLayout],
  template: `
    <app-step-layout [step]="step()">
      <p class="step__intro" data-testid="pending-step">
        {{ 'publish.coming-soon.text' | transloco }}
      </p>
    </app-step-layout>
  `,
  styleUrl: '../shared/step-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PendingStep {
  /** From the data of the route (`withComponentInputBinding` in the shell, `data` otherwise). */
  readonly step = input.required<PublishStep>();
}
