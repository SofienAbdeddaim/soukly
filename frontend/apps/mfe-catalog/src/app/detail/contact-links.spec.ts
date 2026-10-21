import { callLink, contactLoginUrl, formatPhone, listingPath, whatsappLink } from './contact-links';

describe('contact links', () => {
  it('formats a Tunisian number in groups, any other number as given', () => {
    expect(formatPhone('+21620123456')).toBe('+216 20 123 456');
    expect(formatPhone('+33612345678')).toBe('+33612345678');
  });

  it('calls the number in E.164', () => {
    expect(callLink('+21620123456')).toBe('tel:+21620123456');
  });

  it('opens WhatsApp on the number without "+" with the message encoded', () => {
    const link = whatsappLink(
      '+21620123456',
      'Bonjour, votre annonce « Peugeot 208 » sur Soukly m’intéresse : https://soukly.tn/fr/listings/1',
    );
    expect(link.startsWith('https://wa.me/21620123456?text=')).toBe(true);
    expect(decodeURIComponent(link.split('?text=')[1])).toBe(
      'Bonjour, votre annonce « Peugeot 208 » sur Soukly m’intéresse : https://soukly.tn/fr/listings/1',
    );
    expect(link).not.toContain('+');
  });

  it('comes back to the listing with contact=1 after the login', () => {
    expect(listingPath('ar', 'abc')).toBe('/ar/listings/abc');
    expect(contactLoginUrl('fr', 'abc')).toBe(
      '/fr/account/login?returnUrl=%2Ffr%2Flistings%2Fabc%3Fcontact%3D1',
    );
  });
});
