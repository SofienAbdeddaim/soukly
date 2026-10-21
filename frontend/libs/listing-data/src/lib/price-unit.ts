import { AttributeValue } from './models';

/** Period a real-estate price is paid for, shown after it: "1 100 DT / mois". */
export type PriceUnit = 'month' | 'night';

/**
 * Unit of the price of a listing, deduced from its category and its `transaction` (CAT-01): a
 * holiday rental per night, a flat share or a property for rent per month, nothing otherwise.
 */
export function priceUnit(
  categoryCode: string,
  attributes: Record<string, AttributeValue>,
): PriceUnit | null {
  if (!categoryCode.startsWith('real-estate.')) {
    return null;
  }
  if (categoryCode === 'real-estate.holiday-rentals') {
    return 'night';
  }
  if (categoryCode === 'real-estate.roommates' || attributes['transaction'] === 'rent') {
    return 'month';
  }
  return null;
}
