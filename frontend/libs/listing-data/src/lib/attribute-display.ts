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

/** How an attribute is rendered in the publish form, from its type and number of values only. */
export type AttributeControlKind = 'text' | 'integer' | 'toggles' | 'select' | 'checkbox';

export function controlKind(attribute: AttributeDefinition): AttributeControlKind {
  switch (attribute.type) {
    case 'TEXT':
      return 'text';
    case 'INTEGER':
      return 'integer';
    case 'BOOLEAN':
      return 'checkbox';
    case 'ENUM':
      return (attribute.values?.length ?? 0) <= MAX_TOGGLE_VALUES ? 'toggles' : 'select';
  }
}

/** Digits typed in a number field, thousands separators (spaces, dots, commas) allowed. */
const INTEGER_TEXT = /^[\d\s.,\u00a0\u202f\u066c]+$/u;

/** The whole number typed, null when empty, NaN when it is not a whole number. */
export function parseInteger(value: string): number | null {
  const text = value.trim();
  if (!text) {
    return null;
  }
  return INTEGER_TEXT.test(text) ? Number(text.replace(/\D/gu, '')) : Number.NaN;
}

/** "62000" -> "62 000" (fr), "62,000" (en), in Latin digits; numbers below 5 digits unchanged. */
export function formatInteger(
  value: number,
  attribute: AttributeDefinition,
  locale: string,
): string {
  return usesGrouping(attribute)
    ? new Intl.NumberFormat(`${locale}-u-nu-latn`, { useGrouping: true }).format(value)
    : String(value);
}

/**
 * Unit of a number attribute, read from the end of its label in the language of the page:
 * "Kilométrage (km)" -> "km", "المساحة (م²)" -> "م²". Null when the label names none.
 */
export function attributeUnit(label: string): string | null {
  return /\(([^()]+)\)\s*$/u.exec(label)?.[1].trim() ?? null;
}

/** A label without its unit: "Kilométrage (km)" -> "Kilométrage". */
export function withoutUnit(label: string): string {
  return label.replace(/\s*\([^()]+\)\s*$/u, '');
}
