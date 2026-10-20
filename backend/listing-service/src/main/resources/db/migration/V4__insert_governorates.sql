-- CAT-01: the 24 governorates of Tunisia; fr and ar labels from the CAT-01 ticket, en labels written for CAT-01
-- and reviewed in its pull request. Replayable: an existing code is left untouched.

INSERT INTO governorates (code, label_fr, label_ar, label_en, sort_order) VALUES
    ('tunis',       'Tunis',       'تونس',     'Tunis',       10),
    ('ariana',      'Ariana',      'أريانة',   'Ariana',      20),
    ('ben-arous',   'Ben Arous',   'بن عروس',  'Ben Arous',   30),
    ('manouba',     'Manouba',     'منوبة',    'Manouba',     40),
    ('nabeul',      'Nabeul',      'نابل',     'Nabeul',      50),
    ('zaghouan',    'Zaghouan',    'زغوان',    'Zaghouan',    60),
    ('bizerte',     'Bizerte',     'بنزرت',    'Bizerte',     70),
    ('beja',        'Béja',        'باجة',     'Beja',        80),
    ('jendouba',    'Jendouba',    'جندوبة',   'Jendouba',    90),
    ('kef',         'Le Kef',      'الكاف',    'El Kef',      100),
    ('siliana',     'Siliana',     'سليانة',   'Siliana',     110),
    ('sousse',      'Sousse',      'سوسة',     'Sousse',      120),
    ('monastir',    'Monastir',    'المنستير', 'Monastir',    130),
    ('mahdia',      'Mahdia',      'المهدية',  'Mahdia',      140),
    ('sfax',        'Sfax',        'صفاقس',    'Sfax',        150),
    ('kairouan',    'Kairouan',    'القيروان', 'Kairouan',    160),
    ('kasserine',   'Kasserine',   'القصرين',  'Kasserine',   170),
    ('sidi-bouzid', 'Sidi Bouzid', 'سيدي بوزيد', 'Sidi Bouzid', 180),
    ('gabes',       'Gabès',       'قابس',     'Gabes',       190),
    ('medenine',    'Médenine',    'مدنين',    'Medenine',    200),
    ('tataouine',   'Tataouine',   'تطاوين',   'Tataouine',   210),
    ('gafsa',       'Gafsa',       'قفصة',     'Gafsa',       220),
    ('tozeur',      'Tozeur',      'توزر',     'Tozeur',      230),
    ('kebili',      'Kébili',      'قبلي',     'Kebili',      240)
ON CONFLICT (code) DO NOTHING;
