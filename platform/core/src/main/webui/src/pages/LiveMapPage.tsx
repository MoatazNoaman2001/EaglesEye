import { useEffect, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import { useTranslation } from "react-i18next";

const STATUS_COLORS: Record<string, string> = {
  MOVING: "#2e8f57",
  IDLING: "#c98a1b",
  STOPPED: "#5b6b7d",
  NO_FIX: "#c93a2e",
};

// a vehicle with no update for this long is considered gone (drops off + decrements)
const STALE_MS = 45_000;

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
  const seenAtRef = useRef<Map<string, number>>(new Map());
  const fittedRef = useRef(false);
  const [vehicles, setVehicles] = useState<Record<string, LiveVehicle>>({});
  const [listOpen, setListOpen] = useState(false);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
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

    const upsert = (v: LiveVehicle) => {
      const lat = parseFloat(v.lat);
      const lon = parseFloat(v.lon);
      if (!v.vehicleId || isNaN(lat) || isNaN(lon)) return;
      seenAtRef.current.set(v.vehicleId, Date.now());
      setVehicles((prev) => ({ ...prev, [v.vehicleId]: v }));

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
        const popup = new maplibregl.Popup({ offset: 14 }).setHTML(html);
        const marker = new maplibregl.Marker({ element: el }).setLngLat([lon, lat]).setPopup(popup).addTo(map);
        markersRef.current.set(v.vehicleId, { marker, popup });
      }
      if (!fittedRef.current) {
        map.flyTo({ center: [lon, lat], zoom: 14, duration: 600 });
        fittedRef.current = true;
      }
    };

    const remove = (id: string) => {
      markersRef.current.get(id)?.marker.remove();
      markersRef.current.delete(id);
      seenAtRef.current.delete(id);
      setVehicles((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
    };

    // WebSocket live push (T-503) — replaces polling
    let ws: WebSocket | null = null;
    let reconnectTimer: number | undefined;
    const connect = () => {
      const proto = location.protocol === "https:" ? "wss" : "ws";
      ws = new WebSocket(`${proto}://${location.host}/ws/live`);
      ws.onopen = () => setConnected(true);
      ws.onclose = () => {
        setConnected(false);
        reconnectTimer = window.setTimeout(connect, 2000);   // auto-reconnect
      };
      ws.onmessage = (msg) => {
        try {
          const data = JSON.parse(msg.data);
          if (data.type === "snapshot") {
            (data.vehicles as LiveVehicle[]).forEach(upsert);
          } else if (data.type === "position") {
            const e = data.event;
            const t2 = e.telemetry;
            upsert({
              vehicleId: e.vehicleId,
              vehicleLabel: e.vehicleLabel,
              status: e.status,
              lat: String(t2.latitude),
              lon: String(t2.longitude),
              speedKmh: t2.speedKmh != null ? String(Math.round(t2.speedKmh * 10) / 10) : undefined,
              headingDeg: t2.headingDeg != null ? String(t2.headingDeg) : undefined,
              imei: t2.imei,
              protocol: t2.protocol,
              deviceTime: t2.deviceTime,
            });
          }
        } catch {
          /* ignore malformed frame */
        }
      };
    };
    connect();

    // staleness sweep: drop vehicles that stopped reporting (decrements the count)
    const sweep = window.setInterval(() => {
      const now = Date.now();
      for (const [id, at] of seenAtRef.current) {
        if (now - at > STALE_MS) remove(id);
      }
    }, 5000);

    return () => {
      window.clearInterval(sweep);
      window.clearTimeout(reconnectTimer);
      ws?.close();
      markersRef.current.forEach((m) => m.marker.remove());
      markersRef.current.clear();
      map.remove();
      mapRef.current = null;
    };
  }, []);

  const list = Object.values(vehicles);

  const focusVehicle = (v: LiveVehicle) => {
    const lat = parseFloat(v.lat);
    const lon = parseFloat(v.lon);
    if (isNaN(lat) || isNaN(lon) || !mapRef.current) return;
    mapRef.current.flyTo({ center: [lon, lat], zoom: 15, duration: 800 });
    const m = markersRef.current.get(v.vehicleId);
    if (m && !m.popup.isOpen()) m.marker.togglePopup();
    setListOpen(false);
  };

  return (
    <div className="page fill" style={{ position: "relative" }}>
      <div className="live-panel">
        <button className="map-count" onClick={() => setListOpen((o) => !o)} aria-expanded={listOpen}>
          <span className={`live-dot ${connected ? "on" : "off"}`} />
          <b>{list.length}</b> {t("live.vehiclesLive")}
          <span className={`chev ${listOpen ? "up" : ""}`}>▾</span>
        </button>
        {listOpen && (
          <div className="live-list">
            {list.length === 0 && <div className="live-empty">{t("live.none")}</div>}
            {list.map((v) => (
              <button key={v.vehicleId} className="live-row" onClick={() => focusVehicle(v)}>
                <span className="dot" style={{ background: STATUS_COLORS[v.status] || "#5b6b7d" }} />
                <span className="lbl">{v.vehicleLabel || v.imei}</span>
                <span className="meta">{v.status} · {v.speedKmh ?? "0"} km/h</span>
              </button>
            ))}
          </div>
        )}
      </div>
      <div ref={containerRef} className="map-container" />
    </div>
  );
}
