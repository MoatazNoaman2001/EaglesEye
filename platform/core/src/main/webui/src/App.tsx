import { NavLink, Route, Routes } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { toggleLanguage } from "./i18n";
import LiveMapPage from "./pages/LiveMapPage";
import VehiclesPage from "./pages/VehiclesPage";
import TripsPage from "./pages/TripsPage";
import AlertsPage from "./pages/AlertsPage";
import RulesPage from "./pages/RulesPage";
import SettingsPage from "./pages/SettingsPage";

function Logo() {
  return (
    <svg viewBox="0 0 120 120">
      <path d="M16 76 L22 44 L40 24 L62 18 L86 28 L112 50 L96 56 L102 64 L86 62 L74 66 L66 74 L56 68 L50 80 L40 74 L34 86 L24 80 Z" fill="#1f3a5f" />
      <path d="M40 24 L62 18 L86 28 L64 40 Z" fill="#3d8fd6" />
      <path d="M86 28 L112 50 L96 56 L82 44 Z" fill="#5ea8e0" />
      <circle cx="64" cy="46" r="12" fill="none" stroke="#1fc3f0" strokeWidth="3" />
      <circle cx="64" cy="46" r="4.5" fill="#1fc3f0" />
    </svg>
  );
}

export default function App() {
  const { t } = useTranslation();
  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <Logo />
          <div className="name">Eagles<span>Eye</span></div>
        </div>
        <nav className="nav">
          <NavLink to="/" end>{t("nav.liveMap")}</NavLink>
          <NavLink to="/vehicles">{t("nav.vehicles")}</NavLink>
          <NavLink to="/trips">{t("nav.trips")}</NavLink>
          <NavLink to="/alerts">{t("nav.alerts")}</NavLink>
          <NavLink to="/rules">{t("nav.rules")}</NavLink>
          <NavLink to="/settings">{t("nav.settings")}</NavLink>
        </nav>
        <div className="foot">
          <button className="lang-btn" onClick={toggleLanguage}>{t("langSwitch")}</button>
        </div>
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<LiveMapPage />} />
          <Route path="/vehicles" element={<VehiclesPage />} />
          <Route path="/trips" element={<TripsPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/rules" element={<RulesPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Routes>
      </main>
    </div>
  );
}
