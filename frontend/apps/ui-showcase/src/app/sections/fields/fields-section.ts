import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { LucideEye, LucideEyeOff } from '@lucide/angular';
import {
  UiCheckbox,
  UiCheckboxControl,
  UiField,
  UiFieldControl,
  UiFieldPrefix,
  UiFieldSuffix,
} from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/** ui-field around input, select and textarea (hint, error, required, prefix, suffix), ui-checkbox. */
@Component({
  selector: 'app-fields-section',
  imports: [
    ShowcaseSection,
    LucideEye,
    LucideEyeOff,
    UiCheckbox,
    UiCheckboxControl,
    UiField,
    UiFieldControl,
    UiFieldPrefix,
    UiFieldSuffix,
  ],
  templateUrl: './fields-section.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FieldsSection {
  protected readonly t = inject(ShowcaseLocale).labels;
  protected readonly showPassword = signal(false);
}
