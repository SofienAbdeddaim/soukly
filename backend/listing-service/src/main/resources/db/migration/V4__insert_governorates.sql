-- CAT-01: the 24 governorates of Tunisia with their INS code (see V5); fr and ar labels from the CAT-01 ticket, en
-- labels written for CAT-01 and reviewed in its pull request. Replayable: an existing code is left untouched.

INSERT INTO governorates (code, ins_code, label_fr, label_ar, label_en, sort_order) VALUES
    ('tunis',       '11', 'Tunis',       'تونس',     'Tunis',       10),
    ('ariana',      '12', 'Ariana',      'أريانة',   'Ariana',      20),
    ('ben-arous',   '13', 'Ben Arous',   'بن عروس',  'Ben Arous',   30),
    ('manouba',     '14', 'Manouba',     'منوبة',    'Manouba',     40),
    ('nabeul',      '15', 'Nabeul',      'نابل',     'Nabeul',      50),
    ('zaghouan',    '16', 'Zaghouan',    'زغوان',    'Zaghouan',    60),
    ('bizerte',     '17', 'Bizerte',     'بنزرت',    'Bizerte',     70),
    ('beja',        '21', 'Béja',        'باجة',     'Beja',        80),
    ('jendouba',    '22', 'Jendouba',    'جندوبة',   'Jendouba',    90),
    ('kef',         '23', 'Le Kef',      'الكاف',    'El Kef',      100),
    ('siliana',     '24', 'Siliana',     'سليانة',   'Siliana',     110),
    ('sousse',      '31', 'Sousse',      'سوسة',     'Sousse',      120),
    ('monastir',    '32', 'Monastir',    'المنستير', 'Monastir',    130),
    ('mahdia',      '33', 'Mahdia',      'المهدية',  'Mahdia',      140),
    ('sfax',        '34', 'Sfax',        'صفاقس',    'Sfax',        150),
    ('kairouan',    '41', 'Kairouan',    'القيروان', 'Kairouan',    160),
    ('kasserine',   '42', 'Kasserine',   'القصرين',  'Kasserine',   170),
    ('sidi-bouzid', '43', 'Sidi Bouzid', 'سيدي بوزيد', 'Sidi Bouzid', 180),
    ('gabes',       '51', 'Gabès',       'قابس',     'Gabes',       190),
    ('medenine',    '52', 'Médenine',    'مدنين',    'Medenine',    200),
    ('tataouine',   '53', 'Tataouine',   'تطاوين',   'Tataouine',   210),
    ('gafsa',       '61', 'Gafsa',       'قفصة',     'Gafsa',       220),
    ('tozeur',      '62', 'Tozeur',      'توزر',     'Tozeur',      230),
    ('kebili',      '63', 'Kébili',      'قبلي',     'Kebili',      240)
ON CONFLICT (code) DO NOTHING;
