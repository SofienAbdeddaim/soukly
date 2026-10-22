import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { UiButton } from '@soukly/ui';

export interface DisconnectDialogData {
  network: 'facebook' | 'instagram';
  /** Name of the connected Page, or "@username" of the Instagram account. */
  name: string;
}

/**
 * "Disconnect this Page?" (SOC-01) or "Disconnect this Instagram account?" (SOC-02): an alert
 * dialog, the safe "Cancel" focused first. Closes with `true` when the member confirms.
 */
@Component({
  selector: 'app-disconnect-dialog',
  imports: [TranslocoPipe, UiButton],
  template: `
    <h2 id="disconnect-dialog-title" class="disconnect__title">
      {{ keys() + '.title' | transloco }}
    </h2>
    <p id="disconnect-dialog-text" class="disconnect__text">
      {{ 'account.social.disconnect.text-before' | transloco
      }}<bdi class="disconnect__name">{{ data.name }}</bdi
      >{{ keys() + '.text-after' | transloco }}
    </p>
    <div class="disconnect__actions">
      <button
        ui-button
        variant="danger"
        type="button"
        data-testid="disconnect-confirm"
        (click)="dialogRef.close(true)"
      >
        {{ 'account.social.disconnect.confirm' | transloco }}
      </button>
      <button
        ui-button
        variant="ghost"
        type="button"
        cdkFocusInitial
        data-testid="disconnect-cancel"
        (click)="dialogRef.close()"
      >
        {{ 'account.social.disconnect.cancel' | transloco }}
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

    .disconnect__title {
      font-family: var(--font-body);
      font-size: 1.125rem;
      font-weight: 600;
    }

    .disconnect__text {
      line-height: 1.5;
    }

    .disconnect__name {
      font-weight: 600;
    }

    .disconnect__actions {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-testid': 'disconnect-dialog' },
})
export class DisconnectDialog {
  protected readonly data = inject<DisconnectDialogData>(DIALOG_DATA);
  protected readonly dialogRef = inject<DialogRef<boolean>>(DialogRef);

  /** Texts of the network: the title and the end of the sentence. */
  protected readonly keys = computed(() =>
    this.data.network === 'instagram'
      ? 'account.social.disconnect-instagram'
      : 'account.social.disconnect',
  );
}
