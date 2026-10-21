import { attributePrefix, usesGrouping } from './attribute-display';
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
