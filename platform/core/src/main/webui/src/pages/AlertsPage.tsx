import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

type Alert = {
  id: string;
  vehicleLabel?: string;
  vehicleId: string;
  ruleName?: string;
  type: string;
  severity: string;
  message: string;
  time: string;
  acknowledged: boolean;
};

export default function AlertsPage() {
  const { t } = useTranslation();
  const [alerts, setAlerts] = useState<Alert[]>([]);

  const load = useCallback(() => {
    fetch("/api/v1/alerts").then((r) => r.json()).then(setAlerts).catch(() => {});
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, [load]);

  const ack = async (id: string) => {
    await fetch(`/api/v1/alerts/${id}/ack`, { method: "PUT" });
    load();
  };

  const open = alerts.filter((a) => !a.acknowledged).length;

  return (
    <div className="page">
      <h1 className="title">{t("alerts.title")}</h1>
      <p className="subtitle">
        {t("alerts.subtitle")} — <b>{open}</b> {t("alerts.open")}
      </p>
      <table className="data">
        <thead>
          <tr>
            <th>{t("alerts.severity")}</th>
            <th>{t("alerts.message")}</th>
            <th>{t("alerts.rule")}</th>
            <th>{t("alerts.time")}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {alerts.map((a) => (
            <tr key={a.id} className={a.acknowledged ? "row-acked" : ""}>
              <td><span className={`sev ${a.severity}`}>{t("severity." + a.severity, a.severity)}</span></td>
              <td>{a.message}</td>
              <td className="mutedcell">{a.ruleName ?? a.type}</td>
              <td className="mutedcell">{a.time.replace("T", " ").slice(0, 19)}</td>
              <td>
                {a.acknowledged
                  ? <span className="badge closed">{t("alerts.acked")}</span>
                  : <button className="btn-small" onClick={() => ack(a.id)}>{t("alerts.ack")}</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {alerts.length === 0 && <p className="subtitle">{t("alerts.none")}</p>}
    </div>
  );
}
