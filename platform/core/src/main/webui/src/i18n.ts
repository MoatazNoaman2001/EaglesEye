import i18n from "i18next";
import { initReactI18next } from "react-i18next";

// Arabic-first platform (FR-LOC-01): both locales from day one, full RTL flip.
const resources = {
  en: {
    translation: {
      nav: { liveMap: "Live Map", vehicles: "Vehicles", trips: "Trips", alerts: "Alerts", rules: "Alert Rules", settings: "Settings" },
      alerts: { title: "Alerts", subtitle: "What the platform caught", open: "unacknowledged", severity: "Severity", message: "Message", rule: "Rule", time: "Time", ack: "Acknowledge", acked: "Acknowledged", none: "No alerts yet — that is good news." },
      rules: { title: "Alert Rules", subtitle: "Rules become live in the pipeline within a minute — no redeploy", on: "On", off: "Off", saved: "Saved", error: "Save failed", badJson: "Invalid JSON" },
      severity: { info: "Info", warning: "Warning", critical: "Critical" },
      ruleTypes: { speeding: "Speeding", geofence_entry: "Geofence entry", geofence_exit: "Geofence exit", idle: "Prolonged idling", after_hours: "After-hours movement", low_battery: "Low battery" },
      live: { vehiclesLive: "vehicle(s) live" },
      vehicles: { title: "Vehicles", subtitle: "Fleet vehicles and assets", plate: "Plate", name: "Name", make: "Make", model: "Model", year: "Year", category: "Category" },
      trips: { title: "Trips", subtitle: "Automatically detected trips", vehicle: "Vehicle", start: "Start", end: "End", distance: "Distance", maxSpeed: "Max speed", positions: "Positions", inProgress: "In progress" },
      settings: { title: "Settings", subtitle: "Runtime platform configuration — changes apply without redeploy", save: "Save", saved: "Saved", error: "Save failed" },
      langSwitch: "العربية",
    },
  },
  ar: {
    translation: {
      nav: { liveMap: "الخريطة الحية", vehicles: "المركبات", trips: "الرحلات", alerts: "التنبيهات", rules: "قواعد التنبيه", settings: "الإعدادات" },
      alerts: { title: "التنبيهات", subtitle: "ما رصدته المنصة", open: "غير مؤكدة", severity: "الخطورة", message: "الرسالة", rule: "القاعدة", time: "الوقت", ack: "تأكيد", acked: "مؤكد", none: "لا توجد تنبيهات — وهذا خبر جيد." },
      rules: { title: "قواعد التنبيه", subtitle: "تصبح القواعد نشطة خلال دقيقة — دون إعادة نشر", on: "مفعلة", off: "معطلة", saved: "تم الحفظ", error: "فشل الحفظ", badJson: "صيغة JSON غير صحيحة" },
      severity: { info: "معلومة", warning: "تحذير", critical: "حرج" },
      ruleTypes: { speeding: "تجاوز السرعة", geofence_entry: "دخول منطقة", geofence_exit: "خروج من منطقة", idle: "تكدس مطول", after_hours: "حركة خارج ساعات العمل", low_battery: "بطارية منخفضة" },
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
