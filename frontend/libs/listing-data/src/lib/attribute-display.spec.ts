import {
  attributePrefix,
  attributeUnit,
  controlKind,
  formatInteger,
  parseInteger,
  usesGrouping,
  withoutUnit,
} from './attribute-display';
import { AttributeDefinition } from './models';
import { APARTMENT_ATTRIBUTES, CAR_ATTRIBUTES } from './testing';

const byCode = (code: string) =>
  [...CAR_ATTRIBUTES, ...APARTMENT_ATTRIBUTES].find(
    (attribute) => attribute.code === code,
  ) as AttributeDefinition;

describe('attribute display', () => {
  it('groups the thousands of numbers that can reach 5 digits only', () => {
    expect(usesGrouping(byCode('mileage-km'))).toBe(true);
    expect(usesGrouping(byCode('surface-m2'))).toBe(true);
    expect(usesGrouping(byCode('year'))).toBe(false);
    expect(usesGrouping(byCode('rooms'))).toBe(false);
    expect(usesGrouping(byCode('brand'))).toBe(false);
  });

  it('shows the number of rooms as "S+n"', () => {
    expect(attributePrefix('rooms')).toBe('S+');
    expect(attributePrefix('year')).toBeNull();
  });
});

describe('attribute numbers and units', () => {
  it('parses whole numbers typed with separators', () => {
    expect(parseInteger(' 62 000 ')).toBe(62000);
    expect(parseInteger('1.250')).toBe(1250);
    expect(parseInteger('')).toBeNull();
    expect(parseInteger('12a')).toBeNaN();
  });

  it('formats large numbers only with thousands separators', () => {
    expect(formatInteger(62000, byCode('mileage-km'), 'fr-TN')).toMatch(/^62\s000$/u);
    expect(formatInteger(2019, byCode('year'), 'fr-TN')).toBe('2019');
  });

  it('reads the unit at the end of a label', () => {
    expect(attributeUnit('Kilométrage (km)')).toBe('km');
    expect(attributeUnit('المساحة (م²)')).toBe('م²');
    expect(attributeUnit('Année')).toBeNull();
    expect(withoutUnit('Surface (m²)')).toBe('Surface');
  });

  it('renders an attribute from its type', () => {
    expect(controlKind(byCode('fuel'))).toBe('toggles');
    expect(controlKind(byCode('furnished'))).toBe('checkbox');
    expect(controlKind(byCode('year'))).toBe('integer');
  });
});
