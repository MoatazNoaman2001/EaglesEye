import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

type Trip = {
  id: string;
  vehicleId: string;
  startTime: string;
  endTime?: string;
  distanceKm: number;
  maxSpeedKmh: number;
  positionCount: number;
};

type Vehicle = { id: string; plate: string; name?: string };

export default function TripsPage() {
  const { t } = useTranslation();
  const [trips, setTrips] = useState<Trip[]>([]);
  const [labels, setLabels] = useState<Record<string, string>>({});

  useEffect(() => {
    fetch("/api/v1/trips").then((r) => r.json()).then(setTrips).catch(() => {});
    fetch("/api/v1/vehicles")
      .then((r) => r.json())
      .then((vehicles: Vehicle[]) => {
        const map: Record<string, string> = {};
        vehicles.forEach((v) => { map[v.id] = v.name || v.plate; });
        setLabels(map);
      })
      .catch(() => {});
  }, []);

  const fmt = (iso?: string) => (iso ? iso.replace("T", " ").slice(0, 19) : null);

  return (
    <div className="page">
      <h1 className="title">{t("trips.title")}</h1>
      <p className="subtitle">{t("trips.subtitle")}</p>
      <table className="data">
        <thead>
          <tr>
            <th>{t("trips.vehicle")}</th>
            <th>{t("trips.start")}</th>
            <th>{t("trips.end")}</th>
            <th>{t("trips.distance")}</th>
            <th>{t("trips.maxSpeed")}</th>
            <th>{t("trips.positions")}</th>
          </tr>
        </thead>
        <tbody>
          {trips.map((trip) => (
            <tr key={trip.id}>
              <td><b>{labels[trip.vehicleId] ?? trip.vehicleId.slice(0, 8)}</b></td>
              <td>{fmt(trip.startTime)}</td>
              <td>
                {trip.endTime
                  ? <span>{fmt(trip.endTime)}</span>
                  : <span className="badge open">{t("trips.inProgress")}</span>}
              </td>
              <td>{trip.distanceKm.toFixed(2)} km</td>
              <td>{Math.round(trip.maxSpeedKmh)} km/h</td>
              <td>{trip.positionCount}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
