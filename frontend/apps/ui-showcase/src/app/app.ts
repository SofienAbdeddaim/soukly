import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ShowcaseHeader } from './layout/showcase-header/showcase-header';
import { ButtonsSection } from './sections/buttons/buttons-section';
import { FieldsSection } from './sections/fields/fields-section';
import { ListingCardsSection } from './sections/listing-cards/listing-cards-section';
import { SkeletonsSection } from './sections/skeletons/skeletons-section';
import { ToastsSection } from './sections/toasts/toasts-section';

/**
 * Single page that shows every component of libs/ui in all its states, one section per
 * component. Development tool only, never deployed.
 */
@Component({
  selector: 'app-root',
  imports: [
    ShowcaseHeader,
    ButtonsSection,
    FieldsSection,
    ListingCardsSection,
    SkeletonsSection,
    ToastsSection,
  ],
  template: `
    <main class="mx-auto flex max-w-6xl flex-col gap-12 px-4 py-8 md:px-8">
      <app-showcase-header />
      <app-buttons-section />
      <app-fields-section />
      <app-listing-cards-section />
      <app-skeletons-section />
      <app-toasts-section />
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
