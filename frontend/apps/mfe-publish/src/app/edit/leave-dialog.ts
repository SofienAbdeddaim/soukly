import { DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiButton } from '@soukly/ui';

/**
 * "Leave without saving?" (ANN-05): an alert dialog over the edit page, opened when the member
 * leaves it with changes not saved. Closes with true to leave, false (or Escape) to stay; the
 * safe choice, "Keep editing", has the focus first.
 */
@Component({
  selector: 'app-leave-dialog',
  imports: [TranslocoPipe, UiButton],
  template: `
    <h2 id="leave-dialog-title" class="leave__title">
      {{ 'publish.edit.leave.title' | transloco }}
    </h2>
    <p id="leave-dialog-text" class="leave__text">{{ 'publish.edit.leave.text' | transloco }}</p>
    <div class="leave__actions">
      <button
        ui-button
        type="button"
        cdkFocusInitial
        data-testid="leave-stay"
        (click)="dialogRef.close(false)"
      >
        {{ 'publish.edit.leave.stay' | transloco }}
      </button>
      <button
        ui-button
        variant="ghost"
        type="button"
        data-testid="leave-confirm"
        (click)="dialogRef.close(true)"
      >
        {{ 'publish.edit.leave.confirm' | transloco }}
      </button>
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 12px;
      inline-size: min(26rem, calc(100vi - 32px));
      padding: 20px;
      border-radius: var(--radius-lg);
      background-color: var(--color-bg);
      color: var(--color-text);
      box-shadow: var(--shadow-overlay);
    }

    .leave__title {
      font-family: var(--font-body);
      font-size: 1.125rem;
      font-weight: 600;
    }

    .leave__text {
      line-height: 1.5;
    }

    .leave__actions {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-testid': 'leave-dialog' },
})
export class LeaveDialog {
  protected readonly dialogRef = inject<DialogRef<boolean>>(DialogRef);
}
