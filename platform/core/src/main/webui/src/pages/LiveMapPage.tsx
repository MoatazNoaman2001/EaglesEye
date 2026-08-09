import { useEffect, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import { useTranslation } from "react-i18next";

const STATUS_COLORS: Record<string, string> = {
  MOVING: "#2e8f57",
  IDLING: "#c98a1b",
  STOPPED: "#5b6b7d",
  NO_FIX: "#c93a2e",
};

type LiveVehicle = {
  vehicleId: string;
  vehicleLabel?: string;
  status: string;
  lat: string;
  lon: string;
  speedKmh?: string;
  headingDeg?: string;
  imei: string;
  protocol: string;
  deviceTime?: string;
};

export default function LiveMapPage() {
  const { t } = useTranslation();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markersRef = useRef<Map<string, { marker: maplibregl.Marker; popup: maplibregl.Popup }>>(new Map());
  const fittedRef = useRef(false);
  const [count, setCount] = useState(0);

  useEffect(() => {
    // dev tiles: public OSM raster; production self-hosts (ADR-6)
    const map = new maplibregl.Map({
      container: containerRef.current!,
      style: {
        version: 8,
        sources: {
          osm: {
            type: "raster",
            tiles: ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
            tileSize: 256,
            attribution: "© OpenStreetMap contributors",
          },
        },
        layers: [{ id: "osm", type: "raster", source: "osm" }],
      },
      center: [31.235, 30.045],
      zoom: 11,
    });
    map.addControl(new maplibregl.NavigationControl());
    mapRef.current = map;

    const refresh = async () => {
      try {
        const res = await fetch("/api/v1/live/vehicles");
        if (!res.ok) return;
        const vehicles: LiveVehicle[] = await res.json();
        setCount(vehicles.length);
        const seen = new Set<string>();
        for (const v of vehicles) {
          const lat = parseFloat(v.lat);
          const lon = parseFloat(v.lon);
          if (!v.vehicleId || isNaN(lat) || isNaN(lon)) continue;
          seen.add(v.vehicleId);
          const html =
            `<b>${v.vehicleLabel || v.vehicleId}</b><br>` +
            `${v.status} · ${v.speedKmh ?? "0"} km/h<br>` +
            `${v.imei} · ${v.protocol}<br>` +
            `${(v.deviceTime || "").replace("T", " ").slice(0, 19)}`;
          const existing = markersRef.current.get(v.vehicleId);
          if (existing) {
            existing.marker.setLngLat([lon, lat]);
            existing.marker.getElement().style.background = STATUS_COLORS[v.status] || "#5b6b7d";
            existing.popup.setHTML(html);
          } else {
            const el = document.createElement("div");
            el.className = "veh-marker";
            el.style.background = STATUS_COLORS[v.status] || "#5b6b7d";
            const popup = new maplibregl.Popup({ offset: 12 }).setHTML(html);
            const marker = new maplibregl.Marker({ element: el }).setLngLat([lon, lat]).setPopup(popup).addTo(map);
            markersRef.current.set(v.vehicleId, { marker, popup });
          }
        }
        for (const [id, m] of markersRef.current) {
          if (!seen.has(id)) {
            m.marker.remove();
            markersRef.current.delete(id);
          }
        }
        if (!fittedRef.current && vehicles.length > 0) {
          const bounds = new maplibregl.LngLatBounds();
          vehicles.forEach((v) => bounds.extend([parseFloat(v.lon), parseFloat(v.lat)]));
          map.fitBounds(bounds, { padding: 80, maxZoom: 14 });
          fittedRef.current = true;
        }
      } catch {
        /* transient — next poll retries */
      }
    };

    refresh();
    const interval = setInterval(refresh, 2000);   // WebSocket push replaces polling in T-503
    return () => {
      clearInterval(interval);
      markersRef.current.clear();
      map.remove();
      mapRef.current = null;
    };
  }, []);

  return (
    <div className="page fill" style={{ position: "relative" }}>
      <div className="map-count">
        <b>{count}</b> {t("live.vehiclesLive")}
      </div>
      <div ref={containerRef} className="map-container" />
    </div>
  );
}
