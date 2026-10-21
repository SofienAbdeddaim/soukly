import { priceUnit } from './price-unit';

describe('priceUnit', () => {
  it('reads a rent per month and a holiday rental per night', () => {
    expect(priceUnit('real-estate.apartments', { transaction: 'rent' })).toBe('month');
    expect(priceUnit('real-estate.roommates', {})).toBe('month');
    expect(priceUnit('real-estate.holiday-rentals', { rooms: 2 })).toBe('night');
  });

  it('has no unit for a sale or outside real estate', () => {
    expect(priceUnit('real-estate.apartments', { transaction: 'sale' })).toBeNull();
    expect(priceUnit('real-estate.land', {})).toBeNull();
    expect(priceUnit('vehicles.cars', { transaction: 'rent' })).toBeNull();
  });
});
