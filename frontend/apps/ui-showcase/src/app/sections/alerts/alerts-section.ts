import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { UiAlert, UiBadge } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/**
 * ui-alert: info, success and error. Not live here, so the page does not announce them on load.
 * ui-badge: its four tones.
 */
@Component({
  selector: 'app-alerts-section',
  imports: [ShowcaseSection, UiAlert, UiBadge],
  template: `
    <app-showcase-section [heading]="t().alerts" panelClass="grid gap-3 md:max-w-xl">
      <ui-alert type="info" [live]="false">{{ t().alertInfo }}</ui-alert>
      <ui-alert type="success" [live]="false">{{ t().alertSuccess }}</ui-alert>
      <ui-alert type="error" [live]="false">{{ t().alertError }}</ui-alert>
      <p class="flex flex-wrap gap-2">
        <ui-badge tone="success">{{ t().badges[0] }}</ui-badge>
        <ui-badge tone="warning">{{ t().badges[1] }}</ui-badge>
        <ui-badge tone="danger">{{ t().badges[2] }}</ui-badge>
        <ui-badge>{{ t().badges[3] }}</ui-badge>
      </p>
    </app-showcase-section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlertsSection {
  protected readonly t = inject(ShowcaseLocale).labels;
}
