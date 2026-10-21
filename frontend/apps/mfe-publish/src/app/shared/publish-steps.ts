import { Lang } from '@soukly/i18n';
import { Listing } from '@soukly/listing-data';

/** The five steps of the form, in their order (decision of 2026-10-12): also their URL segment. */
export const STEPS = ['category', 'photos', 'details', 'price-location', 'preview'] as const;

export type PublishStep = (typeof STEPS)[number];

/** Position of a step, from 1 to 5. */
export function stepNumber(step: PublishStep): number {
  return STEPS.indexOf(step) + 1;
}

export function previousStep(step: PublishStep): PublishStep | null {
  return STEPS[STEPS.indexOf(step) - 1] ?? null;
}

export function nextStep(step: PublishStep): PublishStep | null {
  return STEPS[STEPS.indexOf(step) + 1] ?? null;
}

/** Step where a member of `missingFields` (or a field of a validation error) is entered. */
export function stepOfField(field: string): PublishStep {
  switch (field) {
    case 'categoryCode':
      return 'category';
    case 'photos':
      return 'photos';
    case 'priceMode':
    case 'price':
    case 'negotiable':
    case 'governorateCode':
    case 'delegationCode':
      return 'price-location';
    default:
      // title, description and every attributes.<code>
      return 'details';
  }
}

/** Where a draft opens when it is resumed: its first incomplete step, the preview when complete. */
export function firstIncompleteStep(listing: Listing): PublishStep {
  const steps = listing.missingFields.map(stepOfField);
  return STEPS.find((step) => steps.includes(step)) ?? 'preview';
}

/** Router commands of a step of a draft: `['/', 'fr', 'publish', id, 'photos']`. */
export function stepCommands(lang: Lang, id: string, step: PublishStep | 'done'): string[] {
  return ['/', lang, 'publish', id, step];
}
