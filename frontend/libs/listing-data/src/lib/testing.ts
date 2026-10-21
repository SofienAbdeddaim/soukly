import { AttributeDefinition, CategoryFamily, Delegation, Governorate, Listing } from './models';

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
