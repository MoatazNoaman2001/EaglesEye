import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

type Vehicle = {
  id: string;
  plate: string;
  name?: string;
  make?: string;
  model?: string;
  modelYear?: number;
  category?: string;
};

export default function VehiclesPage() {
  const { t } = useTranslation();
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);

  useEffect(() => {
    fetch("/api/v1/vehicles").then((r) => r.json()).then(setVehicles).catch(() => {});
  }, []);

  return (
    <div className="page">
      <h1 className="title">{t("vehicles.title")}</h1>
      <p className="subtitle">{t("vehicles.subtitle")}</p>
      <table className="data">
        <thead>
          <tr>
            <th>{t("vehicles.plate")}</th>
            <th>{t("vehicles.name")}</th>
            <th>{t("vehicles.make")}</th>
            <th>{t("vehicles.model")}</th>
            <th>{t("vehicles.year")}</th>
            <th>{t("vehicles.category")}</th>
          </tr>
        </thead>
        <tbody>
          {vehicles.map((v) => (
            <tr key={v.id}>
              <td><b>{v.plate}</b></td>
              <td>{v.name ?? "—"}</td>
              <td>{v.make ?? "—"}</td>
              <td>{v.model ?? "—"}</td>
              <td>{v.modelYear ?? "—"}</td>
              <td>{v.category ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
