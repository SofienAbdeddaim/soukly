import { Lang } from '@soukly/i18n';
import { Labels } from './models';

/** Label of a referential element in the language of the page. */
export function labelOf(labels: Labels, lang: Lang): string {
  return labels[lang];
}

/** Family of a subcategory code: `vehicles.cars` -> `vehicles`. */
export function familyOf(categoryCode: string): string {
  return categoryCode.slice(0, Math.max(0, categoryCode.indexOf('.')));
}
