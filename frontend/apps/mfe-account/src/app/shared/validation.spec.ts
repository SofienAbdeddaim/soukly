import { isEmailFormat, nationalPhone, tunisianDigits } from './validation';

describe('validation helpers', () => {
  it.each([
    ['20 123 456', '20123456'],
    ['20123456', '20123456'],
    ['20.123.456', '20123456'],
    ['+216 20 123 456', '20123456'],
    ['0021620123456', '20123456'],
  ])('reads the 8 digits of %j', (input, digits) => {
    expect(tunisianDigits(input)).toBe(digits);
  });

  it.each(['1234', '20 123 45', '201234567', '2O123456', ''])('refuses %j', (input) => {
    expect(tunisianDigits(input)).toBeNull();
  });

  it('shows an E.164 Tunisian number as typed after +216', () => {
    expect(nationalPhone('+21698765432')).toBe('98 765 432');
    expect(tunisianDigits(nationalPhone('+21698765432'))).toBe('98765432');
  });

  it('checks the usual email format', () => {
    expect(isEmailFormat(' amira@example.tn ')).toBe(true);
    expect(isEmailFormat('amira@example')).toBe(false);
    expect(isEmailFormat('amira example.tn')).toBe(false);
  });
});
