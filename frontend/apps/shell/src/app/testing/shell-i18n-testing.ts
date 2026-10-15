import { i18nTesting, Lang } from '@soukly/i18n';
import ar from '../../../public/i18n/ar.json';
import en from '../../../public/i18n/en.json';
import fr from '../../../public/i18n/fr.json';

/** Transloco for the shell unit tests, with the real root translations. */
export function shellI18nTesting(lang: Lang = 'fr') {
  return i18nTesting({ fr, ar, en }, lang);
}
