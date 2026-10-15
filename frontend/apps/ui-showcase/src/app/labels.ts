/**
 * Texts of the showcase only, in the two directions it demonstrates. The components of
 * libs/ui have no text of their own: every label below reaches them through an input.
 * Real translations (Transloco) arrive with SETUP-07.
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
  cards: string;
  favorite: string;
  removeFavorite: string;
  listings: { title: string; price: string; city: string; date: string; type: string }[];
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
    cards: "Cartes d'annonce",
    favorite: 'Ajouter aux favoris',
    removeFavorite: 'Retirer des favoris',
    listings: [
      {
        title: 'Vélo de ville, très bon état',
        price: '180 DT',
        city: 'Sousse',
        date: 'il y a 2 h',
        type: 'Vente',
      },
      {
        title: 'Cours de maths à domicile',
        price: '25 DT / h',
        city: 'Tunis',
        date: 'hier',
        type: 'Service',
      },
      {
        title: 'Appartement S+2 meublé, 85 m²',
        price: '1 250 DT / mois',
        city: 'La Marsa',
        date: 'il y a 3 j',
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
    cards: 'بطاقات الإعلانات',
    favorite: 'أضف إلى المفضلة',
    removeFavorite: 'أزل من المفضلة',
    listings: [
      {
        title: 'دراجة مدينة بحالة ممتازة',
        price: '180 DT',
        city: 'سوسة',
        date: 'منذ ساعتين',
        type: 'بيع',
      },
      {
        title: 'دروس رياضيات في المنزل',
        price: '25 DT / ساعة',
        city: 'تونس',
        date: 'أمس',
        type: 'خدمة',
      },
      {
        title: 'شقة مفروشة S+2، 85 م²',
        price: '1 250 DT / شهر',
        city: 'المرسى',
        date: 'منذ 3 أيام',
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
