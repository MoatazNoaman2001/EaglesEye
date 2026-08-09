#!/usr/bin/env node
// EaglesEye — drive simulator: sends a vehicle on a loop through central Cairo
// via Traccar's OsmAnd port, so the whole chain (bridge -> Kafka -> enricher ->
// Valkey -> live map) has something real to show.
//
// Usage: node drive-sim.mjs [imei] [rounds]
//   default imei 356307042441013, rounds 2 (one round ≈ 60s)

const IMEI = process.argv[2] || "356307042441013";
const ROUNDS = parseInt(process.argv[3] || "2", 10);
const BASE = "http://localhost:5055";
const INTERVAL_MS = 2000;

// waypoints: a loop through downtown Cairo / Corniche / Zamalek
const WAYPOINTS = [
  [30.0444, 31.2357], [30.0480, 31.2340], [30.0521, 31.2318], [30.0563, 31.2301],
  [30.0601, 31.2289], [30.0625, 31.2245], [30.0611, 31.2196], [30.0577, 31.2178],
  [30.0538, 31.2196], [30.0502, 31.2225], [30.0466, 31.2266], [30.0440, 31.2310],
];

function interpolate(points, stepsPerLeg) {
  const path = [];
  for (let i = 0; i < points.length; i++) {
    const [aLat, aLon] = points[i];
    const [bLat, bLon] = points[(i + 1) % points.length];
    for (let s = 0; s < stepsPerLeg; s++) {
      const t = s / stepsPerLeg;
      path.push([aLat + (bLat - aLat) * t, aLon + (bLon - aLon) * t]);
    }
  }
  return path;
}

function bearing(a, b) {
  const dLon = (b[1] - a[1]) * Math.PI / 180;
  const la1 = a[0] * Math.PI / 180, la2 = b[0] * Math.PI / 180;
  const y = Math.sin(dLon) * Math.cos(la2);
  const x = Math.cos(la1) * Math.sin(la2) - Math.sin(la1) * Math.cos(la2) * Math.cos(dLon);
  return Math.round((Math.atan2(y, x) * 180 / Math.PI + 360) % 360);
}

const path = interpolate(WAYPOINTS, 3);
console.log(`driving ${IMEI}: ${ROUNDS} round(s), ${path.length} points/round, 1 point every ${INTERVAL_MS / 1000}s`);

let sent = 0;
for (let r = 0; r < ROUNDS; r++) {
  for (let i = 0; i < path.length; i++) {
    const cur = path[i], next = path[(i + 1) % path.length];
    const speedKmh = 25 + Math.random() * 35;             // vary 25-60 km/h
    const speedKnots = (speedKmh / 1.852).toFixed(1);     // OsmAnd speed is in knots
    const url = `${BASE}/?id=${IMEI}&lat=${cur[0].toFixed(5)}&lon=${cur[1].toFixed(5)}` +
                `&speed=${speedKnots}&bearing=${bearing(cur, next)}`;
    try {
      const res = await fetch(url);
      sent++;
      process.stdout.write(`\r sent ${sent} positions (last: ${cur[0].toFixed(4)}, ${cur[1].toFixed(4)} @ ${speedKmh.toFixed(0)} km/h)   `);
      if (!res.ok) console.warn(`\n HTTP ${res.status} at point ${i}`);
    } catch (e) {
      console.warn(`\n send failed: ${e.message}`);
    }
    await new Promise(r2 => setTimeout(r2, INTERVAL_MS));
  }
}
console.log("\ndrive complete");
