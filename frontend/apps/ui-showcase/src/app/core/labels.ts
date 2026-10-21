/**
 * Texts of the showcase only, in the two directions it demonstrates. The components of
 * libs/ui have no text of their own: every label below reaches them through an input.
 * Prices and dates are not texts: the listing cards format them with the pipes of @soukly/i18n.
 */
export type ShowcaseLang = 'fr' | 'ar';

export interface ShowcaseLabels {
  title: string;
  intro: string;
  toggleDirection: string;
  buttons: string;
  primary: string;
  secondary: string;
  ghost: string;
  large: string;
  loading: string;
  disabled: string;
  withIcon: string;
  next: string;
  link: string;
  fields: string;
  titleLabel: string;
  titleHint: string;
  titlePlaceholder: string;
  descriptionLabel: string;
  categoryLabel: string;
  categoryChoose: string;
  categories: string[];
  priceLabel: string;
  priceError: string;
  phoneLabel: string;
  phoneHint: string;
  passwordLabel: string;
  showPassword: string;
  termsLabel: string;
  termsError: string;
  alerts: string;
  alertInfo: string;
  alertSuccess: string;
  alertError: string;
  cards: string;
  favorite: string;
  removeFavorite: string;
  /** `price` in whole dinars, `unit` appended after the formatted price, `hoursAgo` for the date. */
  listings: {
    title: string;
    price: number;
    unit: string;
    city: string;
    hoursAgo: number;
    type: string;
  }[];
  skeletons: string;
  toasts: string;
  showInfo: string;
  showSuccess: string;
  showError: string;
  infoMessage: string;
  successMessage: string;
  errorMessage: string;
  close: string;
}

export const LABELS: Record<ShowcaseLang, ShowcaseLabels> = {
  fr: {
    title: 'Soukly, design system',
    intro: 'Chaque composant de libs/ui dans tous ses états, en LTR et en RTL.',
    toggleDirection: 'Passer en arabe (RTL)',
    buttons: 'Boutons',
    primary: 'Publier',
    secondary: 'Rechercher',
    ghost: 'Annuler',
    large: 'Déposer une annonce',
    loading: 'Envoi…',
    disabled: 'Indisponible',
    withIcon: 'Ajouter une photo',
    next: 'Suivant',
    link: 'Voir les annonces',
    fields: 'Champs',
    titleLabel: "Titre de l'annonce",
    titleHint: '10 caractères minimum.',
    titlePlaceholder: 'Vélo de ville',
    descriptionLabel: 'Description',
    categoryLabel: 'Catégorie',
    categoryChoose: 'Choisir une catégorie',
    categories: ['Véhicules', 'Immobilier', 'Services'],
    priceLabel: 'Prix (DT)',
    priceError: 'Indiquez un prix en dinars, sans millimes.',
    phoneLabel: 'Téléphone',
    phoneHint: '8 chiffres, après l’indicatif +216.',
    passwordLabel: 'Mot de passe',
    showPassword: 'Afficher le mot de passe',
    termsLabel: "J'accepte les conditions d'utilisation.",
    termsError: 'Acceptez les conditions pour continuer.',
    alerts: 'Messages (alert)',
    alertInfo: 'Votre annonce sera visible après relecture.',
    alertSuccess: 'Votre compte est activé.',
    alertError: 'Email ou mot de passe incorrect.',
    cards: "Cartes d'annonce",
    favorite: 'Ajouter aux favoris',
    removeFavorite: 'Retirer des favoris',
    listings: [
      {
        title: 'Vélo de ville, très bon état',
        price: 180,
        unit: '',
        city: 'Sousse',
        hoursAgo: 2,
        type: 'Vente',
      },
      {
        title: 'Cours de maths à domicile',
        price: 25,
        unit: ' / h',
        city: 'Tunis',
        hoursAgo: 24,
        type: 'Service',
      },
      {
        title: 'Appartement S+2 meublé, 85 m²',
        price: 1250,
        unit: ' / mois',
        city: 'La Marsa',
        hoursAgo: 72,
        type: 'Location',
      },
    ],
    skeletons: 'Chargement (skeleton)',
    toasts: 'Notifications (toast)',
    showInfo: 'Afficher une info',
    showSuccess: 'Afficher un succès',
    showError: 'Afficher une erreur',
    infoMessage: 'Votre brouillon est enregistré.',
    successMessage: 'Annonce publiée.',
    errorMessage: 'La publication a échoué. Réessayez dans un instant.',
    close: 'Fermer',
  },
  ar: {
    title: 'سوقلي، نظام التصميم',
    intro: 'كل مكوّنات libs/ui بجميع حالاتها، من اليسار إلى اليمين ومن اليمين إلى اليسار.',
    toggleDirection: 'Passer en français (LTR)',
    buttons: 'الأزرار',
    primary: 'انشر',
    secondary: 'ابحث',
    ghost: 'إلغاء',
    large: 'أضف إعلانًا',
    loading: 'جارٍ الإرسال…',
    disabled: 'غير متاح',
    withIcon: 'أضف صورة',
    next: 'التالي',
    link: 'تصفّح الإعلانات',
    fields: 'الحقول',
    titleLabel: 'عنوان الإعلان',
    titleHint: '10 أحرف على الأقل.',
    titlePlaceholder: 'دراجة مدينة',
    descriptionLabel: 'الوصف',
    categoryLabel: 'الفئة',
    categoryChoose: 'اختر فئة',
    categories: ['سيارات', 'عقارات', 'خدمات'],
    priceLabel: 'السعر (د.ت)',
    priceError: 'أدخل السعر بالدينار، دون مليمات.',
    phoneLabel: 'الهاتف',
    phoneHint: '8 أرقام بعد المفتاح ‎+216.',
    passwordLabel: 'كلمة المرور',
    showPassword: 'إظهار كلمة المرور',
    termsLabel: 'أوافق على شروط الاستخدام.',
    termsError: 'وافق على الشروط للمتابعة.',
    alerts: 'الرسائل',
    alertInfo: 'سيظهر إعلانك بعد المراجعة.',
    alertSuccess: 'تم تفعيل حسابك.',
    alertError: 'البريد الإلكتروني أو كلمة المرور غير صحيحة.',
    cards: 'بطاقات الإعلانات',
    favorite: 'أضف إلى المفضلة',
    removeFavorite: 'أزل من المفضلة',
    listings: [
      {
        title: 'دراجة مدينة بحالة ممتازة',
        price: 180,
        unit: '',
        city: 'سوسة',
        hoursAgo: 2,
        type: 'بيع',
      },
      {
        title: 'دروس رياضيات في المنزل',
        price: 25,
        unit: ' / ساعة',
        city: 'تونس',
        hoursAgo: 24,
        type: 'خدمة',
      },
      {
        title: 'شقة مفروشة S+2، 85 م²',
        price: 1250,
        unit: ' / شهر',
        city: 'المرسى',
        hoursAgo: 72,
        type: 'كراء',
      },
    ],
    skeletons: 'التحميل',
    toasts: 'الإشعارات',
    showInfo: 'إظهار معلومة',
    showSuccess: 'إظهار نجاح',
    showError: 'إظهار خطأ',
    infoMessage: 'تم حفظ المسودة.',
    successMessage: 'تم نشر الإعلان.',
    errorMessage: 'تعذّر النشر. أعد المحاولة بعد قليل.',
    close: 'إغلاق',
  },
};
