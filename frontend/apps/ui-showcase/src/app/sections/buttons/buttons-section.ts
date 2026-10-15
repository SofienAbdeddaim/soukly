import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { LucideArrowRight, LucideCamera } from '@lucide/angular';
import { UiButton } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/** ui-button: variants, sizes, loading, disabled, icons, link. */
@Component({
  selector: 'app-buttons-section',
  imports: [LucideArrowRight, LucideCamera, ShowcaseSection, UiButton],
  templateUrl: './buttons-section.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ButtonsSection {
  protected readonly t = inject(ShowcaseLocale).labels;
}
