import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { UiAlert } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/** ui-alert: info, success and error. Not live here, so the page does not announce them on load. */
@Component({
  selector: 'app-alerts-section',
  imports: [ShowcaseSection, UiAlert],
  template: `
    <app-showcase-section [heading]="t().alerts" panelClass="grid gap-3 md:max-w-xl">
      <ui-alert type="info" [live]="false">{{ t().alertInfo }}</ui-alert>
      <ui-alert type="success" [live]="false">{{ t().alertSuccess }}</ui-alert>
      <ui-alert type="error" [live]="false">{{ t().alertError }}</ui-alert>
    </app-showcase-section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlertsSection {
  protected readonly t = inject(ShowcaseLocale).labels;
}
