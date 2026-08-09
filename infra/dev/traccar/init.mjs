// EaglesEye dev — Traccar bootstrap (one-shot container, like kafka-init).
// Idempotent: ensures the dev admin account exists and links every known device to it.
// This is the dev-scale version of tenant/device auto-provisioning (T-205 pattern).
const BASE = process.env.TRACCAR_URL || "http://traccar:8082";
const TOKEN = process.env.TRACCAR_TOKEN;
const ADMIN_NAME = process.env.TRACCAR_ADMIN_NAME || "EaglesEye Admin";
const ADMIN_EMAIL = process.env.TRACCAR_ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.TRACCAR_ADMIN_PASSWORD;

if (!TOKEN || !ADMIN_EMAIL || !ADMIN_PASSWORD) {
  console.error("missing TRACCAR_TOKEN / TRACCAR_ADMIN_EMAIL / TRACCAR_ADMIN_PASSWORD");
  process.exit(1);
}

const auth = { "Authorization": `Bearer ${TOKEN}` };
const json = { ...auth, "Content-Type": "application/json" };

async function api(path, options = {}) {
  const res = await fetch(BASE + path, options);
  if (!res.ok && res.status !== 204) throw new Error(`${options.method || "GET"} ${path} -> ${res.status}`);
  if (res.status === 204) return null;
  return res.json();
}

async function waitForTraccar(timeoutMs = 120_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      await api("/api/server", { headers: auth });
      return;
    } catch { await new Promise(r => setTimeout(r, 3000)); }
  }
  throw new Error("Traccar API not reachable within " + timeoutMs / 1000 + "s");
}

const main = async () => {
  await waitForTraccar();
  console.log("traccar API reachable");

  const users = await api("/api/users", { headers: auth });
  let admin = users.find(u => u.email === ADMIN_EMAIL);
  if (admin) {
    console.log(`admin '${ADMIN_EMAIL}' already exists (id=${admin.id})`);
  } else {
    admin = await api("/api/users", {
      method: "POST", headers: json,
      body: JSON.stringify({ name: ADMIN_NAME, email: ADMIN_EMAIL, password: ADMIN_PASSWORD, administrator: true }),
    });
    console.log(`admin '${ADMIN_EMAIL}' created (id=${admin.id})`);
  }

  const devices = await api("/api/devices?all=true", { headers: auth });
  let linked = 0;
  for (const d of devices) {
    try {
      await api("/api/permissions", {
        method: "POST", headers: json,
        body: JSON.stringify({ userId: admin.id, deviceId: d.id }),
      });
      linked++;
    } catch { /* already linked — fine, we're idempotent */ }
  }
  console.log(`devices known: ${devices.length}, newly linked to admin: ${linked}`);
  console.log("traccar-init done");
};

main().catch(err => { console.error("traccar-init FAILED:", err.message); process.exit(1); });
