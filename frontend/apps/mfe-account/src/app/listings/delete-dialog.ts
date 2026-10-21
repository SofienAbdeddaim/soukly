import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiButton } from '@soukly/ui';

export interface DeleteDialogData {
  /** Title of the listing, or its "Untitled" text. */
  title: string;
  /** A published listing may be deactivated instead. */
  canDeactivate: boolean;
}

/** What the member chose; `undefined` when the dialog was cancelled. */
export type DeleteChoice = 'delete' | 'deactivate';

/**
 * "Delete this listing?" (ANN-05): an alert dialog, the safe "Cancel" focused first. A published
 * listing can be deactivated instead, which can be undone.
 */
@Component({
  selector: 'app-delete-dialog',
  imports: [TranslocoPipe, UiButton],
  template: `
    <h2 id="delete-dialog-title" class="delete__title">
      {{ 'account.listings.delete.title' | transloco }}
    </h2>
    <div id="delete-dialog-text" class="delete__text">
      <p>
        {{ 'account.listings.delete.text-before' | transloco
        }}<bdi class="delete__name">{{ data.title }}</bdi
        >{{ 'account.listings.delete.text-after' | transloco }}
      </p>
      <p class="delete__final">{{ 'account.listings.delete.final' | transloco }}</p>
    </div>
    @if (data.canDeactivate) {
      <p class="delete__tip">{{ 'account.listings.delete.tip' | transloco }}</p>
    }
    <div class="delete__actions">
      <button
        ui-button
        variant="danger"
        type="button"
        data-testid="delete-confirm"
        (click)="dialogRef.close('delete')"
      >
        {{ 'account.listings.delete.confirm' | transloco }}
      </button>
      @if (data.canDeactivate) {
        <button
          ui-button
          variant="secondary"
          type="button"
          data-testid="delete-deactivate"
          (click)="dialogRef.close('deactivate')"
        >
          {{ 'account.listings.delete.deactivate' | transloco }}
        </button>
      }
      <button
        ui-button
        variant="ghost"
        type="button"
        cdkFocusInitial
        data-testid="delete-cancel"
        (click)="dialogRef.close()"
      >
        {{ 'account.listings.delete.cancel' | transloco }}
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

    .delete__title {
      font-family: var(--font-body);
      font-size: 1.125rem;
      font-weight: 600;
    }

    .delete__text {
      display: flex;
      flex-direction: column;
      gap: 4px;
      line-height: 1.5;
    }

    .delete__name {
      font-weight: 600;
    }

    .delete__final {
      color: var(--color-danger);
      font-weight: 500;
    }

    .delete__tip {
      padding-block: 10px;
      padding-inline: 12px;
      border-radius: var(--radius-md);
      background-color: var(--color-surface-muted);
      font-size: 0.875rem;
      line-height: 1.5;
    }

    .delete__actions {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-testid': 'delete-dialog' },
})
export class DeleteDialog {
  protected readonly data = inject<DeleteDialogData>(DIALOG_DATA);
  protected readonly dialogRef = inject<DialogRef<DeleteChoice>>(DialogRef);
}
