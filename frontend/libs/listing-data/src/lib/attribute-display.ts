import { AttributeDefinition } from './models';

/**
 * Text shown before the value of an attribute. Presentation only, the attribute itself still comes
 * from the referentials: a number of bedrooms reads "S+2" in Tunisia (a living room and 2 rooms).
 */
const PREFIXES: Record<string, string> = { rooms: 'S+' };

export function attributePrefix(code: string): string | null {
  return PREFIXES[code] ?? null;
}

/**
 * Whether an integer attribute shows thousands separators ("62 000 km"): only for numbers that can
 * reach 5 digits, so that a year or a number of rooms stays "2019" and "3".
 */
export function usesGrouping(attribute: AttributeDefinition): boolean {
  return (
    attribute.type === 'INTEGER' && (attribute.maxValue == null || attribute.maxValue >= 10_000)
  );
}

/** Enumerations up to this many values are shown as toggle buttons, a `select` beyond. */
export const MAX_TOGGLE_VALUES = 6;
