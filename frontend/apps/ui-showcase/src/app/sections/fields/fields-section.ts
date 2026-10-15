import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { UiField, UiFieldControl } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/** ui-field around input, select and textarea: hint, error, required. */
@Component({
  selector: 'app-fields-section',
  imports: [ShowcaseSection, UiField, UiFieldControl],
  templateUrl: './fields-section.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FieldsSection {
  protected readonly t = inject(ShowcaseLocale).labels;
}
