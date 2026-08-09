import i18n from "i18next";
import { initReactI18next } from "react-i18next";

// Arabic-first platform (FR-LOC-01): both locales from day one, full RTL flip.
const resources = {
  en: {
    translation: {
      nav: { liveMap: "Live Map", vehicles: "Vehicles", trips: "Trips", settings: "Settings" },
      live: { vehiclesLive: "vehicle(s) live" },
      vehicles: { title: "Vehicles", subtitle: "Fleet vehicles and assets", plate: "Plate", name: "Name", make: "Make", model: "Model", year: "Year", category: "Category" },
      trips: { title: "Trips", subtitle: "Automatically detected trips", vehicle: "Vehicle", start: "Start", end: "End", distance: "Distance", maxSpeed: "Max speed", positions: "Positions", inProgress: "In progress" },
      settings: { title: "Settings", subtitle: "Runtime platform configuration — changes apply without redeploy", save: "Save", saved: "Saved", error: "Save failed" },
      langSwitch: "العربية",
    },
  },
  ar: {
    translation: {
      nav: { liveMap: "الخريطة الحية", vehicles: "المركبات", trips: "الرحلات", settings: "الإعدادات" },
      live: { vehiclesLive: "مركبة نشطة" },
      vehicles: { title: "المركبات", subtitle: "مركبات وأصول الأسطول", plate: "اللوحة", name: "الاسم", make: "الصانع", model: "الطراز", year: "السنة", category: "الفئة" },
      trips: { title: "الرحلات", subtitle: "الرحلات المكتشفة تلقائياً", vehicle: "المركبة", start: "البداية", end: "النهاية", distance: "المسافة", maxSpeed: "أقصى سرعة", positions: "النقاط", inProgress: "جارية" },
      settings: { title: "الإعدادات", subtitle: "إعدادات المنصة — تطبق دون إعادة نشر", save: "حفظ", saved: "تم الحفظ", error: "فشل الحفظ" },
      langSwitch: "English",
    },
  },
};

i18n.use(initReactI18next).init({
  resources,
  lng: localStorage.getItem("ee-lang") || "en",
  fallbackLng: "en",
  interpolation: { escapeValue: false },
});

export function applyDirection(lang: string) {
  document.documentElement.lang = lang;
  document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
}
applyDirection(i18n.language);

export function toggleLanguage() {
  const next = i18n.language === "ar" ? "en" : "ar";
  i18n.changeLanguage(next);
  localStorage.setItem("ee-lang", next);
  applyDirection(next);
}

export default i18n;
