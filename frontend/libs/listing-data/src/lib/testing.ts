import {
  AttributeDefinition,
  CategoryFamily,
  Delegation,
  Governorate,
  Listing,
  OwnListingCard,
  OwnListingStatus,
  PublicListing,
} from './models';

/**
 * Fixtures for unit tests, copied from the referentials of listing-service (CAT-01): two families
 * with their subcategories, and the attributes of cars and apartments.
 */
export const TEST_ID = '0b9d6c3e-6a43-4c5e-9f0e-2d7a1b8c4e51';

/** A draft as answered by the API: only its category is set. */
export function draftListing(overrides: Partial<Listing> = {}): Listing {
  return {
    id: TEST_ID,
    status: 'DRAFT',
    version: 0,
    kind: 'SALE',
    categoryCode: 'vehicles.cars',
    title: null,
    description: null,
    priceMode: null,
    price: null,
    negotiable: false,
    governorateCode: null,
    delegationCode: null,
    attributes: {},
    photos: [],
    missingFields: [
      'photos',
      'title',
      'description',
      'priceMode',
      'governorateCode',
      'attributes.brand',
      'attributes.year',
      'attributes.mileage-km',
      'attributes.fuel',
      'attributes.gearbox',
    ],
    warnings: [],
    createdAt: '2026-10-21T08:00:00Z',
    updatedAt: '2026-10-21T08:00:00Z',
    publishedAt: null,
    expiresAt: null,
    ...overrides,
  };
}

/** A published car on "My listings" (ANN-05), online until 2026-12-18, its number shown 8 times. */
export function ownListingCard(overrides: Partial<OwnListingCard> = {}): OwnListingCard {
  return {
    id: TEST_ID,
    status: 'PUBLISHED',
    title: 'Peugeot 208 diesel, première main',
    kind: 'SALE',
    categoryCode: 'vehicles.cars',
    priceMode: 'FIXED',
    price: 32000,
    currency: 'TND',
    coverThumbnailUrl: 'https://photos.example.test/listings/1/400.webp',
    updatedAt: '2026-10-19T10:00:00Z',
    publishedAt: '2026-10-19T10:00:00Z',
    expiresAt: '2026-12-18T10:00:00Z',
    contactRevealsCount: 8,
    ...overrides,
  };
}

/** Counts of the tabs of "My listings": 3 online, 1 draft, 1 expired, none deactivated. */
export const OWN_COUNTS: Record<OwnListingStatus, number> = {
  PUBLISHED: 3,
  DRAFT: 1,
  EXPIRED: 1,
  INACTIVE: 0,
};

/** A published car as anyone but its owner reads it (CATA-03), with 4 photos. */
export function publicListing(overrides: Partial<PublicListing> = {}): PublicListing {
  return {
    id: TEST_ID,
    status: 'PUBLISHED',
    kind: 'SALE',
    categoryCode: 'vehicles.cars',
    title: 'Peugeot 208 diesel',
    description: 'Première main, carnet d’entretien.\nPneus neufs.',
    priceMode: 'FIXED',
    price: 32000,
    currency: 'TND',
    negotiable: true,
    governorateCode: 'sfax',
    delegationCode: 'sfax.sakiet-ezzit',
    attributes: { brand: 'Peugeot', year: 2019, 'mileage-km': 62000, fuel: 'diesel' },
    photos: [0, 1, 2, 3].map((position) => ({
      id: `photo-${position}`,
      url: `https://photos.example.test/${position}/1600.webp`,
      thumbnailUrl: `https://photos.example.test/${position}/400.webp`,
      width: 1600,
      height: 1200,
      position,
    })),
    publishedAt: '2026-10-21T08:00:00Z',
    seller: {
      id: 'a1b2c3d4-0000-4000-8000-000000000001',
      displayName: 'Mohamed B.',
      memberSince: '2026-03',
    },
    ...overrides,
  };
}

export const TEST_FAMILIES: CategoryFamily[] = [
  {
    code: 'vehicles',
    label: { fr: 'Véhicules', ar: 'المركبات', en: 'Vehicles' },
    subcategories: [
      {
        code: 'vehicles.cars',
        familyCode: 'vehicles',
        label: { fr: 'Voitures', ar: 'سيارات', en: 'Cars' },
      },
      {
        code: 'vehicles.motorcycles',
        familyCode: 'vehicles',
        label: {
          fr: 'Motos et scooters',
          ar: 'دراجات نارية وسكوترات',
          en: 'Motorcycles and scooters',
        },
      },
    ],
  },
  {
    code: 'real-estate',
    label: { fr: 'Immobilier', ar: 'العقارات', en: 'Real estate' },
    subcategories: [
      {
        code: 'real-estate.apartments',
        familyCode: 'real-estate',
        label: { fr: 'Appartements', ar: 'شقق', en: 'Apartments' },
      },
    ],
  },
  {
    code: 'services',
    label: { fr: 'Services', ar: 'الخدمات', en: 'Services' },
    subcategories: [
      {
        code: 'services.home-repair',
        familyCode: 'services',
        label: { fr: 'Travaux et réparations', ar: 'أشغال وإصلاحات', en: 'Works and repairs' },
      },
    ],
  },
];

export const CAR_ATTRIBUTES: AttributeDefinition[] = [
  {
    code: 'brand',
    type: 'TEXT',
    required: true,
    minLength: 2,
    maxLength: 40,
    filterable: true,
    label: { fr: 'Marque', ar: 'الماركة', en: 'Brand' },
  },
  {
    code: 'model',
    type: 'TEXT',
    required: false,
    minLength: 1,
    maxLength: 40,
    filterable: false,
    label: { fr: 'Modèle', ar: 'الطراز', en: 'Model' },
  },
  {
    code: 'year',
    type: 'INTEGER',
    required: true,
    minValue: 1950,
    maxValue: 2027,
    filterable: true,
    label: { fr: 'Année', ar: 'سنة الصنع', en: 'Year' },
  },
  {
    code: 'mileage-km',
    type: 'INTEGER',
    required: true,
    minValue: 0,
    maxValue: 2_000_000,
    filterable: true,
    label: { fr: 'Kilométrage (km)', ar: 'عدد الكيلومترات (كم)', en: 'Mileage (km)' },
  },
  {
    code: 'fuel',
    type: 'ENUM',
    required: true,
    filterable: true,
    label: { fr: 'Carburant', ar: 'الوقود', en: 'Fuel' },
    values: [
      { code: 'petrol', label: { fr: 'Essence', ar: 'بنزين', en: 'Petrol' } },
      { code: 'diesel', label: { fr: 'Diesel', ar: 'ديزل', en: 'Diesel' } },
      { code: 'hybrid', label: { fr: 'Hybride', ar: 'هجين', en: 'Hybrid' } },
      { code: 'electric', label: { fr: 'Électrique', ar: 'كهربائي', en: 'Electric' } },
      { code: 'lpg', label: { fr: 'GPL', ar: 'غاز (GPL)', en: 'LPG' } },
    ],
  },
  {
    code: 'gearbox',
    type: 'ENUM',
    required: true,
    filterable: true,
    label: { fr: 'Boîte de vitesses', ar: 'علبة السرعات', en: 'Gearbox' },
    values: [
      { code: 'manual', label: { fr: 'Manuelle', ar: 'يدوية', en: 'Manual' } },
      { code: 'automatic', label: { fr: 'Automatique', ar: 'أوتوماتيكية', en: 'Automatic' } },
    ],
  },
];

export const APARTMENT_ATTRIBUTES: AttributeDefinition[] = [
  {
    code: 'transaction',
    type: 'ENUM',
    required: true,
    filterable: true,
    label: { fr: "Type d'offre", ar: 'نوع العرض', en: 'Offer type' },
    values: [
      { code: 'sale', label: { fr: 'Vente', ar: 'بيع', en: 'Sale' } },
      { code: 'rent', label: { fr: 'Location', ar: 'كراء', en: 'Rent' } },
    ],
  },
  {
    code: 'surface-m2',
    type: 'INTEGER',
    required: true,
    minValue: 1,
    maxValue: 100_000,
    filterable: true,
    label: { fr: 'Surface (m²)', ar: 'المساحة (م²)', en: 'Area (m²)' },
  },
  {
    code: 'rooms',
    type: 'INTEGER',
    required: true,
    minValue: 0,
    maxValue: 20,
    filterable: true,
    label: { fr: 'Nombre de chambres', ar: 'عدد الغرف', en: 'Bedrooms' },
  },
  {
    code: 'furnished',
    type: 'BOOLEAN',
    required: false,
    filterable: true,
    label: { fr: 'Meublé', ar: 'مفروش', en: 'Furnished' },
  },
];

/** Three of the 24 governorates, in the order of the referentials. */
export const TEST_GOVERNORATES: Governorate[] = [
  { code: 'tunis', label: { fr: 'Tunis', ar: 'تونس', en: 'Tunis' } },
  { code: 'ariana', label: { fr: 'Ariana', ar: 'أريانة', en: 'Ariana' } },
  { code: 'sfax', label: { fr: 'Sfax', ar: 'صفاقس', en: 'Sfax' } },
];

/** Delegations of Sfax (INS 2012). */
export const SFAX_DELEGATIONS: Delegation[] = [
  { code: 'sfax.sfax-ville', label: { fr: 'Sfax Ville', ar: 'صفاقس المدينة', en: 'Sfax Ville' } },
  {
    code: 'sfax.sakiet-ezzit',
    label: { fr: 'Sakiet Ezzit', ar: 'ساقية الزيت', en: 'Sakiet Ezzit' },
  },
];
