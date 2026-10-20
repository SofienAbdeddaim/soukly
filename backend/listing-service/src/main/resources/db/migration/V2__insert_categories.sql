-- CAT-01: the 10 families and 44 subcategories of V1. The ar and en labels were written for CAT-01 and reviewed
-- in its pull request. Replayable: an existing code is left untouched.

INSERT INTO categories (code, parent_code, label_fr, label_ar, label_en, sort_order) VALUES
    ('vehicles',    NULL, 'Véhicules',   'المركبات',          'Vehicles',      10),
    ('real-estate', NULL, 'Immobilier',  'العقارات',          'Real estate',   20),
    ('jobs',        NULL, 'Emploi',      'الشغل',             'Jobs',          30),
    ('services',    NULL, 'Services',    'الخدمات',           'Services',      40),
    ('home',        NULL, 'Maison',      'المنزل',            'Home',          50),
    ('multimedia',  NULL, 'Multimédia',  'الإلكترونيات',      'Multimedia',    60),
    ('fashion',     NULL, 'Mode',        'الموضة',            'Fashion',       70),
    ('leisure',     NULL, 'Loisirs',     'الترفيه',           'Leisure',       80),
    ('animals',     NULL, 'Animaux',     'الحيوانات',         'Animals',       90),
    ('misc',        NULL, 'Divers',      'متفرقات',           'Miscellaneous', 100)
ON CONFLICT (code) DO NOTHING;

INSERT INTO categories (code, parent_code, label_fr, label_ar, label_en, sort_order) VALUES
    ('vehicles.cars',        'vehicles', 'Voitures',               'سيارات',                 'Cars',                      10),
    ('vehicles.motorcycles', 'vehicles', 'Motos et scooters',      'دراجات نارية وسكوترات',  'Motorcycles and scooters',  20),
    ('vehicles.utility',     'vehicles', 'Utilitaires et camions', 'سيارات نفعية وشاحنات',   'Vans and trucks',           30),
    ('vehicles.parts',       'vehicles', 'Pièces et accessoires',  'قطع غيار وإكسسوارات',    'Parts and accessories',     40),

    ('real-estate.apartments',      'real-estate', 'Appartements',          'شقق',                  'Apartments',          10),
    ('real-estate.houses',          'real-estate', 'Maisons et villas',     'منازل وفيلات',         'Houses and villas',   20),
    ('real-estate.land',            'real-estate', 'Terrains',              'أراضٍ',                'Land',                30),
    ('real-estate.commercial',      'real-estate', 'Bureaux et commerces',  'مكاتب ومحلات تجارية',  'Offices and shops',   40),
    ('real-estate.roommates',       'real-estate', 'Colocation',            'سكن مشترك',            'Flatshares',          50),
    ('real-estate.holiday-rentals', 'real-estate', 'Locations de vacances', 'كراء للعطل',           'Holiday rentals',     60),

    ('jobs.offers',  'jobs', 'Offres d''emploi',   'عروض شغل',  'Job offers',  10),
    ('jobs.seekers', 'jobs', 'Demandes d''emploi', 'طلبات شغل', 'Job seekers', 20),

    ('services.tutoring',       'services', 'Cours et soutien scolaire',  'دروس ودعم مدرسي',     'Lessons and tutoring',   10),
    ('services.home-repair',    'services', 'Travaux et réparations',     'أشغال وإصلاحات',      'Works and repairs',      20),
    ('services.moving',         'services', 'Déménagement et transport',  'نقل الأثاث والبضائع', 'Moving and transport',   30),
    ('services.events',         'services', 'Événements',                 'مناسبات وحفلات',      'Events',                 40),
    ('services.beauty',         'services', 'Beauté et bien-être',        'تجميل وعناية',        'Beauty and wellness',    50),
    ('services.other-services', 'services', 'Autres services',            'خدمات أخرى',          'Other services',         60),

    ('home.furniture',  'home', 'Meubles',             'أثاث',               'Furniture',        10),
    ('home.appliances', 'home', 'Électroménager',      'أجهزة كهرومنزلية',   'Home appliances',  20),
    ('home.decor',      'home', 'Décoration',          'ديكور',              'Decoration',       30),
    ('home.garden-diy', 'home', 'Jardin et bricolage', 'حديقة وأشغال يدوية', 'Garden and DIY',   40),

    ('multimedia.phones',    'multimedia', 'Téléphones',          'هواتف',             'Phones',                     10),
    ('multimedia.computers', 'multimedia', 'Ordinateurs',         'حواسيب',            'Computers',                  20),
    ('multimedia.tablets',   'multimedia', 'Tablettes',           'أجهزة لوحية',       'Tablets',                    30),
    ('multimedia.tv-audio',  'multimedia', 'TV, son et vidéo',    'تلفاز وصوت وفيديو', 'TV, audio and video',        40),
    ('multimedia.gaming',    'multimedia', 'Consoles et jeux vidéo', 'أجهزة وألعاب فيديو', 'Consoles and video games', 50),
    ('multimedia.cameras',   'multimedia', 'Photo et caméras',    'تصوير وكاميرات',    'Cameras and photography',    60),

    ('fashion.clothing',         'fashion', 'Vêtements',            'ملابس',             'Clothing',               10),
    ('fashion.shoes',            'fashion', 'Chaussures',           'أحذية',             'Shoes',                  20),
    ('fashion.watches-jewelry',  'fashion', 'Montres et bijoux',    'ساعات ومجوهرات',    'Watches and jewellery',  30),
    ('fashion.bags-accessories', 'fashion', 'Sacs et accessoires',  'حقائب وإكسسوارات',  'Bags and accessories',   40),
    ('fashion.baby-kids',        'fashion', 'Bébé et enfants',      'الرضع والأطفال',    'Baby and kids',          50),

    ('leisure.sports',       'leisure', 'Sport',                    'رياضة',        'Sports',              10),
    ('leisure.bikes',        'leisure', 'Vélos',                    'دراجات هوائية', 'Bicycles',           20),
    ('leisure.books',        'leisure', 'Livres',                   'كتب',          'Books',               30),
    ('leisure.music',        'leisure', 'Instruments de musique',   'آلات موسيقية', 'Musical instruments', 40),
    ('leisure.collectibles', 'leisure', 'Collection',               'مقتنيات',      'Collectibles',        50),

    ('animals.dogs',         'animals', 'Chiens',                   'كلاب',            'Dogs',         10),
    ('animals.cats',         'animals', 'Chats',                    'قطط',             'Cats',         20),
    ('animals.birds',        'animals', 'Oiseaux',                  'طيور',            'Birds',        30),
    ('animals.pet-supplies', 'animals', 'Accessoires pour animaux', 'مستلزمات الحيوانات', 'Pet supplies', 40),

    ('misc.professional', 'misc', 'Matériel professionnel', 'معدات مهنية', 'Professional equipment', 10),
    ('misc.other',        'misc', 'Autres',                 'أخرى',        'Other',                  20)
ON CONFLICT (code) DO NOTHING;
