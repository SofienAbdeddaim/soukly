-- CAT-01: specific attributes of V1, only for vehicles and real estate (decision of 2026-10-12). The other
-- categories only have the common listing fields. Replayable: an existing code is left untouched.

INSERT INTO attribute_definitions (code, type, required, min_value, max_value, max_value_current_year_offset,
        min_length, max_length, filterable, label_fr, label_ar, label_en, sort_order) VALUES
    ('brand',       'TEXT',    true,  NULL, NULL,    NULL, 2,    40,   true,  'Marque',             'الماركة',          'Brand',          10),
    ('model',       'TEXT',    false, NULL, NULL,    NULL, 1,    40,   false, 'Modèle',             'الطراز',           'Model',          20),
    -- Maximum: current year + 1, computed at validation time
    ('year',        'INTEGER', true,  1950, NULL,    1,    NULL, NULL, true,  'Année',              'سنة الصنع',        'Year',           30),
    ('mileage-km',  'INTEGER', true,  0,    2000000, NULL, NULL, NULL, true,  'Kilométrage (km)',   'عدد الكيلومترات (كم)', 'Mileage (km)', 40),
    ('fuel',        'ENUM',    true,  NULL, NULL,    NULL, NULL, NULL, true,  'Carburant',          'الوقود',           'Fuel',           50),
    ('gearbox',     'ENUM',    true,  NULL, NULL,    NULL, NULL, NULL, true,  'Boîte de vitesses',  'علبة السرعات',     'Gearbox',        60),
    ('transaction', 'ENUM',    true,  NULL, NULL,    NULL, NULL, NULL, true,  'Type d''offre',      'نوع العرض',        'Offer type',     110),
    ('surface-m2',  'INTEGER', true,  1,    100000,  NULL, NULL, NULL, true,  'Surface (m²)',       'المساحة (م²)',     'Area (m²)',      120),
    -- Shown as "S+n" (S+2: a living room and two bedrooms) by the front end
    ('rooms',       'INTEGER', true,  0,    20,      NULL, NULL, NULL, true,  'Nombre de chambres', 'عدد الغرف',        'Bedrooms',       130),
    ('furnished',   'BOOLEAN', false, NULL, NULL,    NULL, NULL, NULL, true,  'Meublé',             'مفروش',            'Furnished',      140)
ON CONFLICT (code) DO NOTHING;

INSERT INTO attribute_enum_values (attribute_code, code, label_fr, label_ar, label_en, sort_order) VALUES
    ('fuel',        'petrol',    'Essence',     'بنزين',       'Petrol',    10),
    ('fuel',        'diesel',    'Diesel',      'ديزل',        'Diesel',    20),
    ('fuel',        'hybrid',    'Hybride',     'هجين',        'Hybrid',    30),
    ('fuel',        'electric',  'Électrique',  'كهربائي',     'Electric',  40),
    ('fuel',        'lpg',       'GPL',         'غاز (GPL)',   'LPG',       50),
    ('gearbox',     'manual',    'Manuelle',    'يدوية',       'Manual',    10),
    ('gearbox',     'automatic', 'Automatique', 'أوتوماتيكية', 'Automatic', 20),
    ('transaction', 'sale',      'Vente',       'بيع',         'Sale',      10),
    ('transaction', 'rent',      'Location',    'كراء',        'Rent',      20)
ON CONFLICT (attribute_code, code) DO NOTHING;

-- required NULL: the default of the attribute definition
INSERT INTO category_attributes (category_code, attribute_code, required, sort_order) VALUES
    ('vehicles.cars', 'brand',      NULL, 10),
    ('vehicles.cars', 'model',      NULL, 20),
    ('vehicles.cars', 'year',       NULL, 30),
    ('vehicles.cars', 'mileage-km', NULL, 40),
    ('vehicles.cars', 'fuel',       NULL, 50),
    ('vehicles.cars', 'gearbox',    NULL, 60),

    ('vehicles.motorcycles', 'year',       NULL, 30),
    ('vehicles.motorcycles', 'mileage-km', NULL, 40),

    ('vehicles.utility', 'year',       NULL, 30),
    ('vehicles.utility', 'mileage-km', NULL, 40),
    ('vehicles.utility', 'fuel',       NULL, 50),
    ('vehicles.utility', 'gearbox',    NULL, 60),

    ('real-estate.apartments', 'transaction', NULL, 10),
    ('real-estate.apartments', 'surface-m2',  NULL, 20),
    ('real-estate.apartments', 'rooms',       NULL, 30),
    ('real-estate.apartments', 'furnished',   NULL, 40),

    ('real-estate.houses', 'transaction', NULL, 10),
    ('real-estate.houses', 'surface-m2',  NULL, 20),
    ('real-estate.houses', 'rooms',       NULL, 30),
    ('real-estate.houses', 'furnished',   NULL, 40),

    ('real-estate.land', 'transaction', NULL, 10),
    ('real-estate.land', 'surface-m2',  NULL, 20),

    ('real-estate.commercial', 'transaction', NULL, 10),
    ('real-estate.commercial', 'surface-m2',  NULL, 20),

    ('real-estate.roommates', 'furnished', NULL, 40),

    ('real-estate.holiday-rentals', 'surface-m2', NULL, 20),
    ('real-estate.holiday-rentals', 'rooms',      NULL, 30),
    ('real-estate.holiday-rentals', 'furnished',  NULL, 40)
ON CONFLICT (category_code, attribute_code) DO NOTHING;
