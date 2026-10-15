import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService, UiButton, UiToastType } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/** ToastService: one trigger per toast type. */
@Component({
  selector: 'app-toasts-section',
  imports: [ShowcaseSection, UiButton],
  template: `
    <app-showcase-section [heading]="t().toasts">
      <button ui-button variant="ghost" type="button" (click)="show('info')">
        {{ t().showInfo }}
      </button>
      <button ui-button variant="ghost" type="button" (click)="show('success')">
        {{ t().showSuccess }}
      </button>
      <button ui-button variant="ghost" type="button" (click)="show('error')">
        {{ t().showError }}
      </button>
    </app-showcase-section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastsSection {
  private readonly toasts = inject(ToastService);
  protected readonly t = inject(ShowcaseLocale).labels;

  protected show(type: UiToastType): void {
    const t = this.t();
    const message = { info: t.infoMessage, success: t.successMessage, error: t.errorMessage }[type];
    this.toasts.show({ type, message, closeLabel: t.close });
  }
}
